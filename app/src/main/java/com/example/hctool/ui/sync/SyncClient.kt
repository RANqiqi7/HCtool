package com.example.hctool.ui.sync

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * 同步服务器的 HTTP 客户端：与 server/hctool-server.js 一一对应。
 * 全部是阻塞式调用，只在 IO 线程里用（SyncManager 负责调度）。
 */
class SyncClient(baseUrl: String, private val token: String) {

    /** 去掉末尾斜杠，拼地址时统一处理 */
    private val base = baseUrl.trim().trimEnd('/')

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val octetMedia = "application/octet-stream".toMediaType()

    /** 服务器返回 { rev, data }；data 是空串表示服务器还没有存过数据 */
    data class ServerState(val rev: Int, val data: String)

    /** 推送结果：成功带回新 rev；409 带回服务器当前 rev */
    sealed interface PushResult {
        data class Success(val rev: Int) : PushResult
        data class Conflict(val serverRev: Int) : PushResult
    }

    private fun get(path: String): String {
        val request = Request.Builder()
            .url(base + path)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }

    private fun requestBuilder(path: String): Request.Builder = Request.Builder()
        .url(base + path)
        .header("Authorization", "Bearer $token")

    /** 连通性探测：免鉴权接口，失败抛 IOException */
    fun ping(): Boolean = try {
        JSONObject(get("/api/ping")).optBoolean("ok")
    } catch (e: Exception) {
        false
    }

    fun fetchState(): ServerState {
        val obj = JSONObject(get("/api/state"))
        return ServerState(obj.getInt("rev"), obj.optString("data", ""))
    }

    /** 整库推送：baseRev 必须等于服务器当前 rev，否则返回 Conflict（乐观锁） */
    fun pushState(baseRev: Int, data: String): PushResult {
        val body = JSONObject()
            .put("baseRev", baseRev)
            .put("data", data)
            .toString()
            .toRequestBody(jsonMedia)
        val request = requestBuilder("/api/state").put(body).build()
        http.newCall(request).execute().use { resp ->
            when {
                resp.code == 409 -> {
                    val obj = JSONObject(resp.body?.string() ?: "{}")
                    return PushResult.Conflict(obj.optInt("rev", -1))
                }
                !resp.isSuccessful -> throw IOException("HTTP ${resp.code}")
                else -> {
                    val obj = JSONObject(resp.body?.string() ?: "{}")
                    return PushResult.Success(obj.optInt("rev", baseRev + 1))
                }
            }
        }
    }

    /** 服务器上已有的照片文件名列表 */
    fun listPhotos(): List<String> {
        val arr = JSONArray(get("/api/photos"))
        return List(arr.length()) { arr.getString(it) }
    }

    /** 上传单张照片（原始字节，文件名即键） */
    fun uploadPhoto(name: String, file: File) {
        val request = requestBuilder("/api/photos/${java.net.URLEncoder.encode(name, "UTF-8")}")
            .put(file.readBytes().toRequestBody(octetMedia))
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        }
    }

    /** 下载单张照片到 dest（原子写：先 .tmp 再 rename） */
    fun downloadPhoto(name: String, dest: File) {
        val request = requestBuilder("/api/photos/${java.net.URLEncoder.encode(name, "UTF-8")}")
            .get()
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val bytes = resp.body?.bytes() ?: return
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(dest)) {
                dest.writeBytes(bytes)
                tmp.delete()
            }
        }
    }
}
