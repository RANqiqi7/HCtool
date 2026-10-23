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
 * 设备正在测试的一条关联订单（对应「关联订单列表」里的一根长条）。
 *
 * @param orderNo     订单号
 * @param sampleBatch 样品批次
 * @param boxInDate   样品入箱日期
 * @param boxOutDate  样品出箱日期
 */
data class DeviceOrder(
    val orderNo: String,
    val sampleBatch: String,
    val boxInDate: String,
    val boxOutDate: String,
)

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
 *
 * 以下为设备详情页展示字段（均有默认值，新增设备时可不填）：
 * @param pendingFormCount     待完成表单数量
 * @param completedFormCount   已完成表单数量
 * @param totalFormCount       表单总数
 * @param testingSampleBatches 在测样品批数
 * @param lastRunTime          上次运行时间（展示用字符串）
 * @param currentOrders        正在测试的关联订单（使用中状态展示）
 * @param historyOrders        历史测过的订单号（空闲状态展开查看）
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
    val pendingFormCount: Int = 0,
    val completedFormCount: Int = 0,
    val totalFormCount: Int = 0,
    val testingSampleBatches: Int = 0,
    val lastRunTime: String = "",
    val currentOrders: List<DeviceOrder> = emptyList(),
    val historyOrders: List<String> = emptyList(),
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
    /** 预置三张地图，便于测试多地图平铺切换与地图名联动 */
    private val mainMap = DeviceMap(id = newId(), name = "主地图", rows = 4, cols = 4)
    private val hallMap = DeviceMap(id = newId(), name = "一厅", rows = 3, cols = 5)
    private val labMap = DeviceMap(id = newId(), name = "实验室", rows = 5, cols = 6)

    /** 所有地图 */
    val maps = mutableStateListOf(mainMap, hallMap, labMap)

    /** 所有设备（跨地图）：预置一批测试数据，覆盖两种状态与多张地图，并填充详情页字段 */
    val devices = mutableStateListOf(
        Device(
            newId(), "采集盒 A", "SB01", "HC-100",
            status = DeviceStatus.IDLE, mapId = mainMap.id, cell = 0,
            pendingFormCount = 0, completedFormCount = 18, totalFormCount = 18,
            testingSampleBatches = 0, lastRunTime = "2026-10-20 16:42",
            historyOrders = listOf("DD-240913", "DD-240827", "DD-240715", "DD-240603")
        ),
        Device(
            newId(), "采集盒 B", "SB02", "HC-100",
            status = DeviceStatus.IN_USE, mapId = mainMap.id, cell = 5,
            pendingFormCount = 3, completedFormCount = 12, totalFormCount = 15,
            testingSampleBatches = 2, lastRunTime = "2026-10-23 09:15",
            currentOrders = listOf(
                DeviceOrder("DD-241021", "P2410-A", "2026-10-21", "2026-10-28"),
                DeviceOrder("DD-241018", "P2410-B", "2026-10-18", "2026-10-30")
            ),
            historyOrders = listOf("DD-240930", "DD-240902")
        ),
        Device(
            newId(), "网关", "GW07", "HC-G2",
            status = DeviceStatus.IDLE, mapId = mainMap.id, cell = 6,
            pendingFormCount = 1, completedFormCount = 7, totalFormCount = 8,
            testingSampleBatches = 0, lastRunTime = "2026-10-19 11:03",
            historyOrders = listOf("DD-240810", "DD-240622")
        ),
        Device(
            newId(), "温控器", "DEV-12", "HC-T3",
            status = DeviceStatus.IN_USE, mapId = mainMap.id, cell = 10,
            pendingFormCount = 2, completedFormCount = 9, totalFormCount = 11,
            testingSampleBatches = 1, lastRunTime = "2026-10-23 08:40",
            currentOrders = listOf(
                DeviceOrder("DD-241022", "P2410-C", "2026-10-22", "2026-11-02")
            ),
            historyOrders = listOf("DD-240918")
        ),
        Device(
            newId(), "一厅相机", "CAM03", "HC-C1",
            status = DeviceStatus.IDLE, mapId = hallMap.id, cell = 2,
            pendingFormCount = 0, completedFormCount = 5, totalFormCount = 5,
            testingSampleBatches = 0, lastRunTime = "2026-10-15 14:20",
            historyOrders = listOf("DD-240705", "DD-240518")
        ),
        Device(
            newId(), "一厅音箱", "SPK04", "HC-S1",
            status = DeviceStatus.IN_USE, mapId = hallMap.id, cell = 7,
            pendingFormCount = 4, completedFormCount = 6, totalFormCount = 10,
            testingSampleBatches = 3, lastRunTime = "2026-10-23 07:55",
            currentOrders = listOf(
                DeviceOrder("DD-241020", "P2410-D", "2026-10-20", "2026-10-27")
            ),
            historyOrders = listOf("DD-240911", "DD-240803")
        ),
        Device(
            newId(), "一厅屏控", "CTL05", "HC-P1",
            status = DeviceStatus.IDLE, mapId = hallMap.id, cell = 11,
            pendingFormCount = 0, completedFormCount = 3, totalFormCount = 3,
            testingSampleBatches = 0, lastRunTime = "2026-10-11 10:08",
            historyOrders = listOf("DD-240601")
        ),
        Device(
            newId(), "实验台 A", "LAB01", "HC-L1",
            status = DeviceStatus.IN_USE, mapId = labMap.id, cell = 4,
            pendingFormCount = 5, completedFormCount = 20, totalFormCount = 25,
            testingSampleBatches = 4, lastRunTime = "2026-10-23 10:02",
            currentOrders = listOf(
                DeviceOrder("DD-241023", "P2410-E", "2026-10-23", "2026-11-05"),
                DeviceOrder("DD-241019", "P2410-F", "2026-10-19", "2026-10-31")
            ),
            historyOrders = listOf("DD-240925", "DD-240907", "DD-240816")
        ),
        Device(
            newId(), "实验台 B", "LAB02", "HC-L1",
            status = DeviceStatus.IDLE, mapId = labMap.id, cell = 9,
            pendingFormCount = 2, completedFormCount = 14, totalFormCount = 16,
            testingSampleBatches = 0, lastRunTime = "2026-10-18 17:30",
            historyOrders = listOf("DD-240909", "DD-240728", "DD-240615")
        ),
        Device(
            newId(), "示波器", "SCO06", "HC-O1",
            status = DeviceStatus.IDLE, mapId = labMap.id, cell = 17,
            pendingFormCount = 0, completedFormCount = 22, totalFormCount = 22,
            testingSampleBatches = 0, lastRunTime = "2026-10-17 09:48",
            historyOrders = listOf("DD-240821", "DD-240710")
        ),
        Device(
            newId(), "频谱仪", "SPA08", "HC-F1",
            status = DeviceStatus.IN_USE, mapId = labMap.id, cell = 23,
            pendingFormCount = 6, completedFormCount = 11, totalFormCount = 17,
            testingSampleBatches = 2, lastRunTime = "2026-10-23 08:12",
            currentOrders = listOf(
                DeviceOrder("DD-241022", "P2410-G", "2026-10-22", "2026-11-01")
            ),
            historyOrders = listOf("DD-240930", "DD-240914", "DD-240806")
        ),
    )

    /** 当前选中的地图 id */
    var selectedMapId by mutableStateOf(mainMap.id)

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

    /** 用一份新地图列表整体替换（地图编辑弹窗点保存时调用）；
     *  被删除地图下的所有设备一并清除，保证网格与设备列表同步 */
    fun replaceMaps(newMaps: List<DeviceMap>, newSelectedId: String) {
        val keptIds = newMaps.map { it.id }.toSet()
        // 不在新列表里的地图即被删除：同步移除其下全部设备
        devices.removeAll { it.mapId !in keptIds }
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
