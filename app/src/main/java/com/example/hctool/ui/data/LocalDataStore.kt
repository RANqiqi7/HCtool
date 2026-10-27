package com.example.hctool.ui.data

import android.content.Context
import android.util.Log
import com.example.hctool.ui.device.DeviceStore
import com.example.hctool.ui.order.OrderStore
import java.io.File
import kotlinx.serialization.json.Json

/**
 * 本地数据仓库：把 [AppSnapshot] 以 JSON 落盘到 filesDir/data.json。
 *
 * - 读：文件不存在 / 解析失败一律返回 null，调用方回退空数据（首次安装即空列表）。
 * - 写：先写临时文件再 rename 的原子替换，进程写到一半被杀也不会留下半截 JSON。
 * - encode 结果同时是云同步的报文正文，本地与服务器存的格式完全一致。
 */
object LocalDataStore {
    private const val FILE_NAME = "data.json"

    /** 宽容模式：忽略未知字段，方便以后给数据类加字段而不旧文件作废 */
    val json = Json { ignoreUnknownKeys = true }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** 快照 → JSON 文本（编码唯一出处，落盘与同步共用） */
    fun encode(snapshot: AppSnapshot): String = json.encodeToString(AppSnapshot.serializer(), snapshot)

    /** JSON 文本 → 快照；解析失败返回 null */
    fun decode(text: String): AppSnapshot? =
        runCatching { json.decodeFromString(AppSnapshot.serializer(), text) }
            .onFailure { Log.w("DataDecode", "快照解析失败: ${it.message}") }
            .getOrNull()

    /** 从两个 store 现取一份快照（调用方负责防抖，这里只做纯搬运） */
    fun snapshotOf(deviceStore: DeviceStore, orderStore: OrderStore): AppSnapshot =
        AppSnapshot(
            devices = deviceStore.devices.toList(),
            maps = deviceStore.maps.toList(),
            selectedMapId = deviceStore.selectedMapId,
            orders = orderStore.orders.toList(),
        )

    /** 读磁盘快照；无文件 / 损坏返回 null */
    fun load(context: Context): AppSnapshot? =
        runCatching { file(context).readText() }.getOrNull()?.let(::decode)

    /** 原子写：临时文件 + rename；rename 失败退化为直接覆盖写 */
    fun saveJson(context: Context, text: String) {
        runCatching {
            val dest = file(context)
            val tmp = File(dest.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(dest)) {
                dest.writeText(text)
                tmp.delete()
            }
        }
    }
}
