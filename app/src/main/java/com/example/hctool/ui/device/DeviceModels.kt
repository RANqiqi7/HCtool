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
 * 设备运行状态：决定列表卡片里状态圆点与地图色块的颜色。
 * 空闲 = 绿色，使用中 = 蓝色。
 */
enum class DeviceStatus {
    IDLE,
    IN_USE,
}

/** 运行状态色，全站唯一一套：状态圆点、地图色块都取这两个值 */
val StatusIdleColor = Color(0xFF34C759)
val StatusInUseColor = Color(0xFF0A84FF)

/**
 * 设备当前该用的颜色：走 [computeStatus] 同一套推导，
 * 保证地图色块与列表卡片的状态圆点永远同色。
 */
fun Device.statusColor(orderStore: OrderStore): Color =
    if (computeStatus(orderStore) == DeviceStatus.IN_USE) StatusInUseColor else StatusIdleColor

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

/** 首次启动的空种子：预置演示数据已清除，一切以磁盘快照 / 用户新增为准 */
fun defaultDeviceSeed(): DeviceSeed = DeviceSeed(maps = emptyList(), devices = emptyList(), selectedMapId = "")

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

    /** 当前选中的地图对象；一张地图都没有时为 null（界面需兼容空地图） */
    val selectedMap: DeviceMap?
        get() = maps.firstOrNull { it.id == selectedMapId } ?: maps.firstOrNull()

    /** 取某张地图下的全部设备 */
    fun devicesOf(mapId: String): List<Device> = devices.filter { it.mapId == mapId }

    /** 取某张地图指定格子上的设备，没有则为 null */
    fun deviceAt(mapId: String, cell: Int): Device? =
        devices.firstOrNull { it.mapId == mapId && it.cell == cell }

    /** 新增一台设备 */
    fun addDevice(device: Device) {
        devices.add(device)
    }

    /** 删除一台设备（列表卡片长按二次确认后调用）；格子无人占用，地图色块自然消失 */
    fun removeDevice(id: String) {
        devices.removeAll { it.id == id }
    }

    /**
     * 把一台设备绑定到某订单：往该设备的当前关联订单里追加一条引用（已含该订单号则跳过，不重复加）。
     * 开始测试弹窗里选中设备时调用；sampleBatch/startTime 只是设备侧回退值，
     * 展示时仍优先取订单节点的 IQC 开始 / OQC 结束时间。
     */
    fun bindDeviceToOrder(deviceId: String, orderNo: String, sampleBatch: String, startTime: String) {
        val index = devices.indexOfFirst { it.id == deviceId }
        if (index < 0) return
        val device = devices[index]
        if (device.currentOrders.any { it.orderNo == orderNo } ||
            device.historyOrders.any { it.orderNo == orderNo }
        ) return
        devices[index] = device.copy(
            currentOrders = device.currentOrders + DeviceOrder(orderNo, sampleBatch, startTime, "")
        )
    }

    /**
     * 写回设备详情页编辑模式里允许改动的三个字段（型号 / 编号 / 图片），
     * 其余字段（表单数、关联订单、所在地图与格子等）一律原样保留。
     */
    fun updateDevice(id: String, model: String, number: String, imageUri: String?) {
        val index = devices.indexOfFirst { it.id == id }
        if (index >= 0) {
            devices[index] = devices[index].copy(
                model = model,
                number = number,
                imageUri = imageUri,
            )
        }
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
