package com.example.hctool.ui.data

import com.example.hctool.ui.device.Device
import com.example.hctool.ui.device.DeviceMap
import com.example.hctool.ui.device.DeviceSeed
import com.example.hctool.ui.order.Order
import kotlinx.serialization.Serializable

/**
 * 全量数据快照：整个 App 的订单 / 设备 / 地图就是这一份文档。
 *
 * 本地持久化（filesDir/data.json）与云同步（服务器整库文档）共用同一格式，
 * 服务器不需要理解业务结构，只负责存字符串 + 版本号 rev。
 */
@Serializable
data class AppSnapshot(
    val devices: List<Device> = emptyList(),
    val maps: List<DeviceMap> = emptyList(),
    /** 当前选中的地图 id；为空时恢复侧回退到第一张地图 */
    val selectedMapId: String = "",
    val orders: List<Order> = emptyList(),
)

/** 快照 → 设备存储种子（selectedMapId 失效/为空时兜底第一张地图） */
fun AppSnapshot.toDeviceSeed(): DeviceSeed {
    val validSelected = selectedMapId.takeIf { id -> maps.any { it.id == id } }
    return DeviceSeed(
        maps = maps,
        devices = devices,
        selectedMapId = validSelected ?: maps.firstOrNull()?.id ?: "",
    )
}
