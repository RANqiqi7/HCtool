package com.example.hctool.ui.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.hctool.ui.order.OrderNodeState
import com.example.hctool.ui.order.OrderStore
import kotlinx.serialization.Serializable
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
 * 设备正在测试 / 历史测过的一条关联订单（对应「关联订单列表」里的一根长条）。
 *
 * @param orderNo     订单号
 * @param sampleBatch 样品批次
 * @param startTime   开始时间（设备侧记录的回退值）；订单在订单库中时，
 *                    界面优先取该订单 IQC 节点的开始时间
 * @param endTime     结束时间（设备侧记录的回退值）；订单在订单库中时，
 *                    界面优先取该订单 OQC 节点的结束时间
 */
@Serializable
data class DeviceOrder(
    val orderNo: String,
    val sampleBatch: String,
    val startTime: String,
    val endTime: String,
)

/**
 * 一台设备。
 *
 * @param nickname 设备昵称（卡片主标题、网格色块内文字）
 * @param number   设备编号（一般为一串字母 + 两位序号，用于正则提取序号）
 * @param model    设备型号
 * @param imageUri 备注图片的 Uri 字符串，可为空（图片非必填）
 * @param mapId    所属地图 id
 * @param cell     在地图网格中的位置（row * cols + col）
 *
 * 以下为设备详情页展示字段（均有默认值，新增设备时可不填）：
 * @param pendingFormCount     待完成表单数量
 * @param completedFormCount   已完成表单数量
 * @param totalFormCount       表单总数
 * @param testingSampleBatches 在测样品批数
 * @param lastRunTime          上次运行时间的设备侧回退值（关联订单不在订单库时才用，
 *                             展示时优先取关联订单测试节点的结束时间，见 computeLastRunTime）
 * @param currentOrders        正在测试的关联订单（使用中状态展示）
 * @param historyOrders        历史测过的关联订单（空闲状态展开查看）；
 *                             与 currentOrders 同构，保证关联订单列表行格式统一
 */
@Serializable
data class Device(
    val id: String,
    val nickname: String,
    val number: String,
    val model: String,
    val imageUri: String? = null,
    val mapId: String,
    val cell: Int,
    val pendingFormCount: Int = 0,
    val completedFormCount: Int = 0,
    val totalFormCount: Int = 0,
    val testingSampleBatches: Int = 0,
    val lastRunTime: String = "",
    val currentOrders: List<DeviceOrder> = emptyList(),
    val historyOrders: List<DeviceOrder> = emptyList(),
)

/**
 * 一张网格地图。可自由编辑名字、大小，并支持多张地图。
 *
 * @param rows 行数
 * @param cols 列数
 */
@Serializable
data class DeviceMap(
    val id: String,
    val name: String,
    val rows: Int = 4,
    val cols: Int = 4,
)

/**
 * 设备运行状态不单独存字段，而是由关联订单的节点推导：
 * 只要该设备关联的订单（当前 + 历史）里存在「进行中」的节点，就说明设备在使用中，
 * 否则为空闲。订单不在订单库时视为没有测试中的节点。
 * 这样在订单详情里勾选 / 撤销节点后，设备状态、状态圆点、筛选与底栏 badge 都会同步刷新。
 */
fun Device.computeStatus(orderStore: OrderStore): DeviceStatus {
    val testing = (currentOrders + historyOrders).any { ref ->
        orderStore.findByOrderNo(ref.orderNo)?.nodes
            ?.any { it.state == OrderNodeState.IN_PROGRESS } == true
    }
    return if (testing) DeviceStatus.IN_USE else DeviceStatus.IDLE
}

/**
 * 上次运行时间派生：取最近一个关联订单（当前 + 历史）里「测试」节点的结束时间。
 * 订单按编号（DD-yyMMDD 前缀）升序即时间序，取最大单号里有结束时间的测试节点；
 * 关联订单都不在订单库（多为纯历史单）时回退到设备侧存储的 lastRunTime，
 * 与 computeStatus 同一套「渲染时派生 + 库缺失回退」的路子。
 */
fun Device.computeLastRunTime(orderStore: OrderStore): String {
    val latest = (currentOrders + historyOrders)
        .sortedBy { it.orderNo }
        .lastOrNull { ref ->
            orderStore.findByOrderNo(ref.orderNo)
                ?.nodes?.firstOrNull { it.name == "测试" }?.endTime?.isNotBlank() == true
        }
    return latest?.let { ref ->
        orderStore.findByOrderNo(ref.orderNo)
            ?.nodes?.firstOrNull { it.name == "测试" }?.endTime
    }?.takeIf { it.isNotBlank() } ?: lastRunTime
}

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
 * DeviceStore 的初始种子：地图 + 设备 + 当前选中地图。
 * 首次启动用 [defaultDeviceSeed]，从磁盘快照恢复时用快照数据构造。
 */
data class DeviceSeed(
    val maps: List<DeviceMap>,
    val devices: List<Device>,
    val selectedMapId: String,
)

/** 预置演示数据：三张地图 + 一批设备，覆盖两种状态与多张地图（仅首次启动用，之后以磁盘数据为准） */
fun defaultDeviceSeed(): DeviceSeed {
    // 局部别名：预置数据里的 newId() 改指向伴生对象，保持写法不变
    fun newId() = DeviceStore.newId()
    val mainMap = DeviceMap(id = DeviceStore.newId(), name = "主地图", rows = 4, cols = 4)
    val hallMap = DeviceMap(id = DeviceStore.newId(), name = "一厅", rows = 3, cols = 5)
    val labMap = DeviceMap(id = DeviceStore.newId(), name = "实验室", rows = 5, cols = 6)
    return DeviceSeed(
        maps = listOf(mainMap, hallMap, labMap),
        selectedMapId = mainMap.id,
        devices = listOf(
        Device(
            newId(), "采集盒 A", "SB01", "HC-100",
            mapId = mainMap.id, cell = 0,
            pendingFormCount = 0, completedFormCount = 18, totalFormCount = 18,
            testingSampleBatches = 0, lastRunTime = "2026-10-20 16:42",
            historyOrders = listOf(
                DeviceOrder("DD-240913", "P2409-A", "2026-09-13", "2026-09-20"),
                DeviceOrder("DD-240827", "P2408-B", "2026-08-27", "2026-09-03"),
                DeviceOrder("DD-240715", "P2407-C", "2026-07-15", "2026-07-22"),
                DeviceOrder("DD-240603", "P2406-D", "2026-06-03", "2026-06-10")
            )
        ),
        Device(
            newId(), "采集盒 B", "SB02", "HC-100",
            mapId = mainMap.id, cell = 5,
            pendingFormCount = 3, completedFormCount = 12, totalFormCount = 15,
            testingSampleBatches = 2, lastRunTime = "2026-10-23 09:15",
            currentOrders = listOf(
                DeviceOrder("DD-241021", "P2410-A", "2026-10-21", "2026-10-28"),
                DeviceOrder("DD-241018", "P2410-B", "2026-10-18", "2026-10-30")
            ),
            historyOrders = listOf(
                DeviceOrder("DD-240930", "P2409-E", "2026-09-30", "2026-10-03"),
                DeviceOrder("DD-240902", "P2409-F", "2026-09-02", "2026-09-09")
            )
        ),
        Device(
            newId(), "网关", "GW07", "HC-G2",
            mapId = mainMap.id, cell = 6,
            pendingFormCount = 1, completedFormCount = 7, totalFormCount = 8,
            testingSampleBatches = 0, lastRunTime = "2026-10-19 11:03",
            historyOrders = listOf(
                DeviceOrder("DD-240810", "P2408-G", "2026-08-10", "2026-08-17"),
                DeviceOrder("DD-240622", "P2406-H", "2026-06-22", "2026-06-29")
            )
        ),
        Device(
            newId(), "温控器", "DEV-12", "HC-T3",
            mapId = mainMap.id, cell = 10,
            pendingFormCount = 2, completedFormCount = 9, totalFormCount = 11,
            testingSampleBatches = 1, lastRunTime = "2026-10-23 08:40",
            currentOrders = listOf(
                DeviceOrder("DD-241022", "P2410-C", "2026-10-22", "2026-11-02")
            ),
            historyOrders = listOf(
                DeviceOrder("DD-240918", "P2409-I", "2026-09-18", "2026-09-25")
            )
        ),
        Device(
            newId(), "一厅相机", "CAM03", "HC-C1",
            mapId = hallMap.id, cell = 2,
            pendingFormCount = 0, completedFormCount = 5, totalFormCount = 5,
            testingSampleBatches = 0, lastRunTime = "2026-10-15 14:20",
            historyOrders = listOf(
                DeviceOrder("DD-240705", "P2407-J", "2026-07-05", "2026-07-12"),
                DeviceOrder("DD-240518", "P2405-K", "2026-05-18", "2026-05-25")
            )
        ),
        Device(
            newId(), "一厅音箱", "SPK04", "HC-S1",
            mapId = hallMap.id, cell = 7,
            pendingFormCount = 4, completedFormCount = 6, totalFormCount = 10,
            testingSampleBatches = 3, lastRunTime = "2026-10-23 07:55",
            currentOrders = listOf(
                DeviceOrder("DD-241020", "P2410-D", "2026-10-20", "2026-10-27")
            ),
            historyOrders = listOf(
                DeviceOrder("DD-240911", "P2409-L", "2026-09-11", "2026-09-18"),
                DeviceOrder("DD-240803", "P2408-M", "2026-08-03", "2026-08-10")
            )
        ),
        Device(
            newId(), "一厅屏控", "CTL05", "HC-P1",
            mapId = hallMap.id, cell = 11,
            pendingFormCount = 0, completedFormCount = 3, totalFormCount = 3,
            testingSampleBatches = 0, lastRunTime = "2026-10-11 10:08",
            historyOrders = listOf(
                DeviceOrder("DD-240601", "P2406-N", "2026-06-01", "2026-06-08")
            )
        ),
        Device(
            newId(), "实验台 A", "LAB01", "HC-L1",
            mapId = labMap.id, cell = 4,
            pendingFormCount = 5, completedFormCount = 20, totalFormCount = 25,
            testingSampleBatches = 4, lastRunTime = "2026-10-23 10:02",
            currentOrders = listOf(
                DeviceOrder("DD-241023", "P2410-E", "2026-10-23", "2026-11-05"),
                DeviceOrder("DD-241019", "P2410-F", "2026-10-19", "2026-10-31")
            ),
            historyOrders = listOf(
                DeviceOrder("DD-240925", "P2409-O", "2026-09-25", "2026-10-02"),
                DeviceOrder("DD-240907", "P2409-P", "2026-09-07", "2026-09-14"),
                DeviceOrder("DD-240816", "P2408-Q", "2026-08-16", "2026-08-23")
            )
        ),
        Device(
            newId(), "实验台 B", "LAB02", "HC-L1",
            mapId = labMap.id, cell = 9,
            pendingFormCount = 2, completedFormCount = 14, totalFormCount = 16,
            testingSampleBatches = 0, lastRunTime = "2026-10-18 17:30",
            historyOrders = listOf(
                DeviceOrder("DD-240909", "P2409-R", "2026-09-09", "2026-09-16"),
                DeviceOrder("DD-240728", "P2407-S", "2026-07-28", "2026-08-04"),
                DeviceOrder("DD-240615", "P2406-T", "2026-06-15", "2026-06-22")
            )
        ),
        Device(
            newId(), "示波器", "SCO06", "HC-O1",
            mapId = labMap.id, cell = 17,
            pendingFormCount = 0, completedFormCount = 22, totalFormCount = 22,
            testingSampleBatches = 0, lastRunTime = "2026-10-17 09:48",
            historyOrders = listOf(
                DeviceOrder("DD-240821", "P2408-U", "2026-08-21", "2026-08-28"),
                DeviceOrder("DD-240710", "P2407-V", "2026-07-10", "2026-07-17")
            )
        ),
        Device(
            newId(), "频谱仪", "SPA08", "HC-F1",
            mapId = labMap.id, cell = 23,
            pendingFormCount = 6, completedFormCount = 11, totalFormCount = 17,
            testingSampleBatches = 2, lastRunTime = "2026-10-23 08:12",
            currentOrders = listOf(
                DeviceOrder("DD-241022", "P2410-G", "2026-10-22", "2026-11-01")
            ),
            historyOrders = listOf(
                DeviceOrder("DD-240930", "P2409-E", "2026-09-30", "2026-10-03"),
                DeviceOrder("DD-240914", "P2409-W", "2026-09-14", "2026-09-21"),
                DeviceOrder("DD-240806", "P2408-X", "2026-08-06", "2026-08-13")
            )
        ),
        )
    )
}

/**
 * 设备页的数据存储。提升到 pager 外层持有，避免翻页时页面被销毁导致数据丢失。
 * 用 Compose 可观察集合，任何增删改都会自动触发界面刷新。
 */
class DeviceStore(seed: DeviceSeed = defaultDeviceSeed()) {
    /** 所有地图 */
    val maps = mutableStateListOf<DeviceMap>().apply { addAll(seed.maps) }

    /** 所有设备（跨地图） */
    val devices = mutableStateListOf<Device>().apply { addAll(seed.devices) }

    /** 当前选中的地图 id */
    var selectedMapId by mutableStateOf(seed.selectedMapId)

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
