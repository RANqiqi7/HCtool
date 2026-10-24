/**
 * HCtool 同步服务器 —— 跑在 root 平板的 Termux 里，零 npm 依赖（只用 Node 内置模块）。
 *
 * 职责很薄：存一份「全量数据文档 + 版本号 rev」+ 一堆照片文件，
 * 业务结构（订单/设备/地图）全部由 App 端的 JSON 决定，服务器不解析。
 *
 * 接口（除 /api/ping 外都要带请求头 Authorization: Bearer <token>）：
 *   GET  /api/ping            → { ok, name }            连通性探测，免鉴权
 *   GET  /api/state           → { rev, data }           拉取全量文档
 *   PUT  /api/state           请求体 { baseRev, data }  → 200 { rev } / 409 { rev }
 *   GET  /api/photos          → ["a.jpg", ...]          列出全部照片文件名
 *   GET  /api/photos/:name    → 图片字节流
 *   PUT  /api/photos/:name    → 原始字节流直接存为照片
 *
 * 启动：node hctool-server.js
 * 首次启动自动生成 config.json（含随机 token），把它填进 App 设置页。
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const ROOT = __dirname;
const CONFIG_FILE = path.join(ROOT, 'config.json');
const DB_FILE = path.join(ROOT, 'db.json');
const DB_TMP = DB_FILE + '.tmp';
const PHOTO_DIR = path.join(ROOT, 'photos');

const MAX_JSON_BODY = 10 * 1024 * 1024;   // 数据文档上限 10MB
const MAX_PHOTO_BODY = 30 * 1024 * 1024;  // 单张照片上限 30MB
const PHOTO_NAME_RE = /^[A-Za-z0-9._-]{1,128}$/; // 白名单：防路径穿越

/* ---------------- 配置与状态 ---------------- */

function loadConfig() {
  if (!fs.existsSync(CONFIG_FILE)) {
    const cfg = {
      port: 8080,
      token: crypto.randomBytes(16).toString('hex'),
    };
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(cfg, null, 2));
    console.log('[hctool] 已生成 config.json，访问令牌：' + cfg.token);
    return cfg;
  }
  return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf8'));
}

function loadDb() {
  try {
    const db = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
    if (typeof db.rev === 'number' && typeof db.data === 'string') return db;
  } catch (e) { /* 首次启动或文件损坏，重置 */ }
  return { rev: 0, data: '' };
}

/** 原子写：先落临时文件再 rename，进程中途被杀也不会留下半截 db.json */
function saveDb(db) {
  fs.writeFileSync(DB_TMP, JSON.stringify(db));
  fs.renameSync(DB_TMP, DB_FILE);
}

const config = loadConfig();
const db = loadDb();
if (!fs.existsSync(PHOTO_DIR)) fs.mkdirSync(PHOTO_DIR, { recursive: true });

/* ---------------- 小工具 ---------------- */

function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
  });
  res.end(body);
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > limit) {
        reject(new Error('body too large'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

function authorized(req) {
  const header = req.headers['authorization'] || '';
  const token = String(header).replace(/^Bearer\s+/i, '').trim();
  //  timing-safe 比较，防按字符计时侧信道（个人工具顺手写上）
  const expect = String(config.token || '');
  if (!expect || token.length !== expect.length) return false;
  return crypto.timingSafeEqual(Buffer.from(token), Buffer.from(expect));
}

function photoPath(name) {
  if (!PHOTO_NAME_RE.test(name) || name.includes('..')) return null;
  return path.join(PHOTO_DIR, name);
}

const MIME = { '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp', '.heic': 'image/heic' };

/* ---------------- 路由 ---------------- */

const server = http.createServer(async (req, res) => {
  const url = (req.url || '').split('?')[0];

  try {
    // 连通性探测：免鉴权，供 App「测试连接」按钮使用
    if (req.method === 'GET' && url === '/api/ping') {
      return sendJson(res, 200, { ok: true, name: 'hctool-sync', rev: db.rev });
    }

    if (!authorized(req)) {
      return sendJson(res, 401, { error: 'unauthorized' });
    }

    /* ---- 数据文档 ---- */

    if (req.method === 'GET' && url === '/api/state') {
      return sendJson(res, 200, { rev: db.rev, data: db.data });
    }

    if (req.method === 'PUT' && url === '/api/state') {
      let body;
      try {
        body = JSON.parse((await readBody(req, MAX_JSON_BODY)).toString('utf8'));
      } catch (e) {
        return sendJson(res, 400, { error: 'bad json body' });
      }
      if (typeof body.data !== 'string') {
        return sendJson(res, 400, { error: 'data must be a string' });
      }
      // 乐观锁：客户端必须带着它拉取时的 rev 来推，防止覆盖别人的更新
      if (body.baseRev !== db.rev) {
        return sendJson(res, 409, { rev: db.rev });
      }
      db.rev += 1;
      db.data = body.data;
      saveDb(db);
      console.log('[hctool] 数据已更新 rev=' + db.rev + ' (' + db.data.length + ' 字节)');
      return sendJson(res, 200, { rev: db.rev });
    }

    /* ---- 照片 ---- */

    if (req.method === 'GET' && url === '/api/photos') {
      const names = fs.readdirSync(PHOTO_DIR).filter((n) => !n.startsWith('.'));
      return sendJson(res, 200, names);
    }

    if (url.startsWith('/api/photos/')) {
      const name = decodeURIComponent(url.slice('/api/photos/'.length));
      const file = photoPath(name);
      if (!file) return sendJson(res, 400, { error: 'bad photo name' });

      if (req.method === 'GET' || req.method === 'HEAD') {
        if (!fs.existsSync(file)) return sendJson(res, 404, { error: 'not found' });
        const stat = fs.statSync(file);
        const ext = path.extname(name).toLowerCase();
        res.writeHead(200, {
          'Content-Type': MIME[ext] || 'application/octet-stream',
          'Content-Length': stat.size,
        });
        if (req.method === 'HEAD') return res.end();
        return fs.createReadStream(file).pipe(res);
      }

      if (req.method === 'PUT') {
        let buf;
        try {
          buf = await readBody(req, MAX_PHOTO_BODY);
        } catch (e) {
          return sendJson(res, 413, { error: 'photo too large' });
        }
        const tmp = file + '.tmp';
        fs.writeFileSync(tmp, buf);
        fs.renameSync(tmp, file);
        console.log('[hctool] 照片已存 ' + name + ' (' + buf.length + ' 字节)');
        return sendJson(res, 200, { ok: true, name });
      }
    }

    return sendJson(res, 404, { error: 'not found' });
  } catch (e) {
    console.error('[hctool] 请求异常：' + (e && e.message));
    return sendJson(res, 500, { error: 'internal' });
  }
});

server.listen(config.port, '0.0.0.0', () => {
  console.log('[hctool] 同步服务已启动：http://0.0.0.0:' + config.port);
  console.log('[hctool] 当前 rev=' + db.rev + '，照片目录=' + PHOTO_DIR);
  console.log('[hctool] 访问令牌见 config.json：' + config.token);
});
