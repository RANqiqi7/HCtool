# HCtool 同步服务器部署指南(平板 Termux 版)

把 root 平板变成 HCtool 的后端:存全量数据 + 照片,多台设备通过它同步。
外网访问走 Tailscale 组网(免端口映射、免公网服务器)。

## 一、平板端:安装 Termux + Node

1. **安装 Termux**:不要用应用商店版(已停更),去 F-Droid 或 GitHub 下载:
   - https://f-droid.org/packages/com.termux/
2. Termux 里执行:
   ```bash
   pkg update
   pkg install nodejs
   ```
3. **放置服务器文件**:把项目里的 `server/hctool-server.js` 传到平板,
   放到 Termux 家目录下的 `hctool-server/` 文件夹。任选一种方式:
   - 用 `termux-setup-storage` 授权后,从下载目录复制:
     ```bash
     termux-setup-storage
     mkdir -p ~/hctool-server
     cp /sdcard/Download/hctool-server.js ~/hctool-server/
     ```
   - 或电脑直推(平板开 USB 调试):
     ```powershell
     adb push server/hctool-server.js /sdcard/Download/
     ```
     再在 Termux 里 `cp /sdcard/Download/hctool-server.js ~/hctool-server/`

## 二、启动与保活

```bash
# 前台试运行(首次会自动生成 config.json,打印访问令牌,记下来!)
cd ~/hctool-server
node hctool-server.js
```

确认打印「同步服务已启动」后,按 Ctrl+C 停掉,配置后台常驻:

```bash
# 1) 禁止系统休眠杀进程
termux-wake-lock

# 2) 后台启动,日志写到 server.log
cd ~/hctool-server
nohup node hctool-server.js >> server.log 2>&1 &
```

**开机自启**(可选,推荐):
1. F-Droid 安装 **Termux:Boot**
2. Termux 里执行:
   ```bash
   mkdir -p ~/.termux/boot
   cat > ~/.termux/boot/hctool-server.sh <<'EOF'
   #!/data/data/com.termux/files/usr/bin/bash
   termux-wake-lock
   cd ~/hctool-server
   nohup node hctool-server.js >> server.log 2>&1 &
   EOF
   chmod +x ~/.termux/boot/hctool-server.sh
   ```
3. 平板设置里把 Termux / Termux:Boot 的电池策略设为「不限制」
   (root 平板也可用 adb 一条命令:`dumpsys deviceidle whitelist +com.termux`)

## 三、网络:局域网 or Tailscale 外网

### 只在家里用(最简单)
路由器给平板绑定静态 IP(管理后台按 MAC 保留),服务器地址就是:
`http://平板IP:8080`

### 需要外网访问(Tailscale 组网)
1. 手机和平板都安装 **Tailscale**,登录同一个账号
   (Google 账号在国内不可用时,可选 GitHub / Microsoft 登录)
2. 两端都连上后,在平板 Tailscale 里看它的虚拟 IP(100.x.y.z 段)
3. 服务器地址填:`http://100.x.y.z:8080`
   ——手机在哪都能连,流量走 Tailscale 加密隧道

> 平板注意:Tailscale 也要加入电池白名单,否则被系统冻结会掉线。

## 四、验证服务器活着

任意设备浏览器打开 `http://平板地址:8080/api/ping`,看到
`{"ok":true,...}` 即成功(此接口免令牌)。

## 五、App 端配置

打开 HCtool → 设备页右上角齿轮 → 设置页「云同步」卡片:
1. 打开同步开关
2. 服务器地址:`http://100.x.y.z:8080`(或局域网 IP)
3. 访问令牌:平板 `~/hctool-server/config.json` 里的 `token` 字段
4. 点「立即同步」,状态显示成功即可

之后的日常使用无需再管:App 启动自动拉取,改动约 2 秒后自动推送。

## 六、常见问题

| 现象 | 处理 |
| --- | --- |
| 推送提示 409 冲突 | 弹窗里选「以本机为准」或「以服务器为准」即可 |
| 连不上:超时 | 检查平板 Termux 是否活着(`pgrep node`)、Tailscale 两端是否在线、端口是否改了 |
| 连不上:拒绝连接 | 地址/端口写错,或 node 进程没起来 |
| 令牌错误(401) | 重新复制 config.json 里的 token,注意别带空格 |
| 换平板/迁移数据 | 把整个 `~/hctool-server` 目录(db.json + photos/)拷走即可,数据都在里面 |

## 安全说明

- 所有接口(除 /api/ping)都要求 Bearer 令牌;令牌泄露=数据全泄露,丢了就在 config.json 里换一个再重启服务。
- 传输走 HTTP,但套在 Tailscale 加密隧道里,实际链路是加密的。
- 本服务为个人工具设计:单令牌、无多用户、无 HTTPS 证书。若以后要对公网裸奔,请换 Nginx/Caddy 反代加 HTTPS。
