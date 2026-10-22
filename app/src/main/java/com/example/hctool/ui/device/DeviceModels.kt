package com.example.hctool.ui.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * 设备运行状态：决定列表卡片里状态圆点的颜色。
 * 空闲 = 绿色，使用中 = 蓝色。
 */
enum class DeviceStatus {
    IDLE,
    IN_USE,
}

/** 状态圆点颜色：空闲绿、使用中蓝 */
val StatusIdleColor = Color(0xFF34C759)
val StatusInUseColor = Color(0xFF0A84FF)

/** 网格里设备色块的默认填充色（简易圆角色块，默认绿色） */
val DeviceBlockColor = Color(0xFF34C759)

/**
 * 一台设备。
 *
 * @param nickname 设备昵称（卡片主标题、网格色块内文字）
 * @param number   设备编号（一般为一串字母 + 两位序号，用于正则提取序号）
 * @param model    设备型号
 * @param imageUri 备注图片的 Uri 字符串，可为空（图片非必填）
 * @param status   运行状态
 * @param mapId    所属地图 id
 * @param cell     在地图网格中的位置（row * cols + col）
 */
data class Device(
    val id: String,
    val nickname: String,
    val number: String,
    val model: String,
    val imageUri: String? = null,
    val status: DeviceStatus = DeviceStatus.IDLE,
    val mapId: String,
    val cell: Int,
)

/**
 * 一张网格地图。可自由编辑名字、大小，并支持多张地图。
 *
 * @param rows 行数
 * @param cols 列数
 */
data class DeviceMap(
    val id: String,
    val name: String,
    val rows: Int = 4,
    val cols: Int = 4,
)

/**
 * 序号提取正则：匹配「一串字母 + 可选分隔符 + 两位数字」，捕获两位序号。
 * 例如 SB07 → 07、DEV-12 → 12、Device 03 → 03。
 * (?!\d) 保证只取末尾两位，不会把三位以上的编号截错。
 */
private val DeviceSeqRegex = Regex("""[A-Za-z]+[-_\s]?(\d{2})(?!\d)""")

/**
 * 从设备编号中提取两位序号；提取不到时回退为原始编号，仍为空则返回 null。
 */
fun extractSequence(number: String): String? {
    if (number.isBlank()) return null
    val match = DeviceSeqRegex.find(number)
    return match?.groupValues?.get(1) ?: number.trim().ifBlank { null }
}

/**
 * 设备页的数据存储。提升到 pager 外层持有，避免翻页时页面被销毁导致数据丢失。
 * 用 Compose 可观察集合，任何增删改都会自动触发界面刷新。
 */
class DeviceStore {
    /** 所有地图，初始给一张 4x4 的主地图 */
    val maps = mutableStateListOf(DeviceMap(id = newId(), name = "主地图", rows = 4, cols = 4))

    /** 所有设备（跨地图） */
    val devices = mutableStateListOf<Device>()

    /** 当前选中的地图 id */
    var selectedMapId by mutableStateOf(maps.first().id)

    /** 当前选中的地图对象 */
    val selectedMap: DeviceMap
        get() = maps.firstOrNull { it.id == selectedMapId } ?: maps.first()

    /** 取某张地图下的全部设备 */
    fun devicesOf(mapId: String): List<Device> = devices.filter { it.mapId == mapId }

    /** 取某张地图指定格子上的设备，没有则为 null */
    fun deviceAt(mapId: String, cell: Int): Device? =
        devices.firstOrNull { it.mapId == mapId && it.cell == cell }

    /** 新增一台设备 */
    fun addDevice(device: Device) {
        devices.add(device)
    }

    /** 新建一张地图并切换过去 */
    fun addMap(name: String, rows: Int, cols: Int): String {
        val id = newId()
        maps.add(DeviceMap(id = id, name = name, rows = rows, cols = cols))
        selectedMapId = id
        return id
    }

    /** 修改地图的名字与大小 */
    fun updateMap(id: String, name: String, rows: Int, cols: Int) {
        val index = maps.indexOfFirst { it.id == id }
        if (index >= 0) {
            maps[index] = maps[index].copy(name = name, rows = rows, cols = cols)
        }
    }

    /** 切换当前地图 */
    fun selectMap(id: String) {
        selectedMapId = id
    }

    /** 切换到下一张地图（循环），用于地图卡片内左右滑动 */
    fun selectNext() {
        if (maps.isEmpty()) return
        val index = maps.indexOfFirst { it.id == selectedMapId }
        selectedMapId = maps[(index + 1) % maps.size].id
    }

    /** 切换到上一张地图（循环），用于地图卡片内左右滑动 */
    fun selectPrev() {
        if (maps.isEmpty()) return
        val index = maps.indexOfFirst { it.id == selectedMapId }
        selectedMapId = maps[(if (index - 1 < 0) maps.size - 1 else index - 1)].id
    }

    /** 用一份新地图列表整体替换（地图编辑弹窗点保存时调用） */
    fun replaceMaps(newMaps: List<DeviceMap>, newSelectedId: String) {
        maps.clear()
        maps.addAll(newMaps)
        selectedMapId = newSelectedId
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/** 记住一个设备存储实例 */
@Composable
fun rememberDeviceStore(): DeviceStore = remember { DeviceStore() }
