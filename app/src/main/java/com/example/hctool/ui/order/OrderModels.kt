package com.example.hctool.ui.order

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 订单状态：进行中 / 已完结。
 * 列表按「进行中优先」排序。
 */
enum class OrderStatus {
    IN_PROGRESS,
    COMPLETED,
}

/** 订单状态副标题颜色：进行中蓝、已完结绿 */
val OrderInProgressColor = Color(0xFF0A84FF)
val OrderCompletedColor = Color(0xFF34C759)

/** 订单状态展示文案 */
fun OrderStatus.label(): String = if (this == OrderStatus.IN_PROGRESS) "进行中" else "已完结"

/** 订单状态副标题颜色 */
fun OrderStatus.color(): Color =
    if (this == OrderStatus.IN_PROGRESS) OrderInProgressColor else OrderCompletedColor

/**
 * 订单节点状态：由开始 / 结束时间推导，不额外存字段，避免两边不一致。
 * 未开始（checkbox 空白）→ 进行中（半选，已记开始时间）→ 已完成（选中，已记结束时间）。
 */
enum class OrderNodeState {
    NOT_STARTED,
    IN_PROGRESS,
    DONE,
}

/**
 * 订单里的一个流程节点（默认 IQC / 测试 / OQC，按顺序进行）。
 *
 * @param name      节点名称
 * @param startTime 开始时间：第一次点 checkbox 时记录，空表示还没开始
 * @param endTime   结束时间：第二次点 checkbox 时记录，空表示还没完成
 */
@Serializable
data class OrderNode(
    val name: String,
    val startTime: String = "",
    val endTime: String = "",
) {
    /** 当前状态：有结束时间即已完成，只有开始时间即进行中，都没有即未开始 */
    val state: OrderNodeState
        get() = when {
            endTime.isNotBlank() -> OrderNodeState.DONE
            startTime.isNotBlank() -> OrderNodeState.IN_PROGRESS
            else -> OrderNodeState.NOT_STARTED
        }
}

/** 默认节点序列：IQC → 测试 → OQC */
fun defaultOrderNodes(): List<OrderNode> = listOf(
    OrderNode("IQC"),
    OrderNode("测试"),
    OrderNode("OQC"),
)

/** 完成进度（0f ~ 1f）：已完成节点数 / 节点总数 */
fun List<OrderNode>.completedFraction(): Float =
    if (isEmpty()) 0f else count { it.state == OrderNodeState.DONE } / size.toFloat()

/**
 * 订单里的一张表单（默认 使用记录表 / runcard）。
 * 与节点不同：表单只有「未填写 / 已填写」两态，没有进行中，也不分先后顺序。
 *
 * @param name     表单名称
 * @param fillTime 填写时间（勾选时记录），空表示还没填
 */
@Serializable
data class OrderForm(
    val name: String,
    val fillTime: String = "",
) {
    /** 是否已填写 */
    val filled: Boolean get() = fillTime.isNotBlank()
}

/** 默认表单：使用记录表 + runcard */
fun defaultOrderForms(): List<OrderForm> = listOf(
    OrderForm("使用记录表"),
    OrderForm("runcard"),
)

/** 表单完成度（0f ~ 1f）：已填写数 / 表单总数。
 *  名字与节点的 completedFraction 区分开，避免两者擦除后 JVM 签名撞车。 */
fun List<OrderForm>.filledFraction(): Float =
    if (isEmpty()) 0f else count { it.filled } / size.toFloat()

/**
 * 一个订单。
 *
 * @param orderNo       订单编号（必填）
 * @param customer      客户代称（必填）
 * @param sampleName    样品名称（选填）
 * @param sampleCount   样品数量（选填）
 * @param sampleNo      样品编号（选填）
 * @param testCondition 测试条件（选填）
 * @param caseOwner     Case Owner（选填）
 * @param imageUri      订单图片 Uri 字符串，可为空
 * @param submitTime    提单时间（保存时由后端记录，精确到年月日时分，格式 yyyy-MM-dd HH:mm）
 * @param nodes         流程节点（详情页「完成进度」卡片按顺序展示，默认 IQC / 测试 / OQC）
 * @param forms         表单（详情页「表单进度」卡片展示，默认 使用记录表 / runcard）
 * @param testHours     开始测试时填写的预计测试时长（单位小时，整数字符串）；
 *                      测试节点进行中时据此算「预计结束时间」，完成后换算成「X天Y小时」记入基础信息
 */
@Serializable
data class Order(
    val id: String,
    val orderNo: String,
    val customer: String,
    val sampleName: String = "",
    val sampleCount: String = "",
    val sampleNo: String = "",
    val testCondition: String = "",
    val caseOwner: String = "",
    val imageUri: String? = null,
    val submitTime: String = "",
    val nodes: List<OrderNode> = defaultOrderNodes(),
    val forms: List<OrderForm> = defaultOrderForms(),
    val testHours: String = "",
)

/**
 * 订单状态不单独存字段，而是由进度推导：
 * 节点进度与表单进度都到 100% 才算已完结，否则一律进行中。
 * 这样勾选 / 撤销任意节点或表单后，列表与详情页的状态文案永远和进度一致。
 */
val Order.status: OrderStatus
    get() = if (nodes.completedFraction() >= 1f && forms.filledFraction() >= 1f) {
        OrderStatus.COMPLETED
    } else {
        OrderStatus.IN_PROGRESS
    }

/**
 * 订单页的数据存储。提升到 pager 外层持有，避免翻页时页面被销毁导致数据丢失。
 * 用 Compose 可观察集合，任何增删改都会自动触发界面刷新。
 */
class OrderStore(initialOrders: List<Order> = emptyList()) {
    /** 订单列表；列表展示时进行中优先 */
    val orders = mutableStateListOf<Order>().apply { addAll(initialOrders) }

    /** 新增一个订单 */
    fun addOrder(order: Order) {
        orders.add(order)
    }

    /**
     * 删除一个订单（列表卡片长按二次确认后调用）。
     * 设备侧的关联订单引用不级联删除：订单不在库时展示回退设备侧记录（与 computeStatus / 行渲染既有设计一致）。
     */
    fun removeOrder(id: String) {
        orders.removeAll { it.id == id }
    }

    /**
     * 写回订单详情页编辑模式下的基础信息（六个文本字段 + 图片），
     * 订单号、提单时间、节点与表单状态等其余字段一律原样保留。
     */
    fun updateBasicInfo(
        id: String,
        customer: String,
        sampleName: String,
        sampleCount: String,
        sampleNo: String,
        testCondition: String,
        caseOwner: String,
        imageUri: String?,
    ) {
        val index = orders.indexOfFirst { it.id == id }
        if (index >= 0) {
            orders[index] = orders[index].copy(
                customer = customer,
                sampleName = sampleName,
                sampleCount = sampleCount,
                sampleNo = sampleNo,
                testCondition = testCondition,
                caseOwner = caseOwner,
                imageUri = imageUri,
            )
        }
    }

    /**
     * 点某个节点的 checkbox，按「未开始 → 进行中 → 已完成」推进一格并记录当时时间。
     * 已完成的节点再点一次视为撤销完成：清掉它的结束时间，
     * 并把它后面所有节点重置为未开始（顺序被打断，后续节点重新排队）。
     */
    fun advanceNode(orderId: String, index: Int) {
        val orderIndex = orders.indexOfFirst { it.id == orderId }
        if (orderIndex < 0) return
        val order = orders[orderIndex]
        if (index !in order.nodes.indices) return
        val nodes = order.nodes.toMutableList()
        when (nodes[index].state) {
            OrderNodeState.NOT_STARTED ->
                nodes[index] = nodes[index].copy(startTime = currentNodeTime())

            OrderNodeState.IN_PROGRESS ->
                nodes[index] = nodes[index].copy(endTime = currentNodeTime())

            OrderNodeState.DONE -> {
                nodes[index] = nodes[index].copy(endTime = "")
                for (i in index + 1 until nodes.size) {
                    nodes[i] = nodes[i].copy(startTime = "", endTime = "")
                }
            }
        }
        orders[orderIndex] = order.copy(nodes = nodes)
    }

    /**
     * 启动「测试」节点：记下开始测试弹窗里填写的预计测试小时数，并给测试节点打开始时间。
     * 只有测试节点尚未开始（开始时间为空）时才写入开始时间，避免重复启动覆盖真实时间。
     */
    fun startTest(orderId: String, hours: String) {
        val orderIndex = orders.indexOfFirst { it.id == orderId }
        if (orderIndex < 0) return
        val order = orders[orderIndex]
        val nodes = order.nodes.toMutableList()
        val index = nodes.indexOfFirst { it.name == "测试" }
        if (index in nodes.indices && nodes[index].startTime.isBlank()) {
            nodes[index] = nodes[index].copy(startTime = currentNodeTime())
        }
        orders[orderIndex] = order.copy(nodes = nodes, testHours = hours.trim())
    }

    /**
     * 点某张表单的 checkbox：未填写 → 记一个填写时间；已填写 → 清空（撤销）。
     * 表单之间没有先后顺序，任意一张都可独立勾选 / 撤销。
     */
    fun toggleForm(orderId: String, index: Int) {
        val orderIndex = orders.indexOfFirst { it.id == orderId }
        if (orderIndex < 0) return
        val order = orders[orderIndex]
        if (index !in order.forms.indices) return
        val forms = order.forms.toMutableList()
        forms[index] = if (forms[index].filled) {
            forms[index].copy(fillTime = "")
        } else {
            forms[index].copy(fillTime = currentNodeTime())
        }
        orders[orderIndex] = order.copy(forms = forms)
    }

    /**
     * 按订单号查订单，查不到返回 null（不新建）。
     * 设备详情页给关联订单画状态圆点时用它判断进行中 / 已完结。
     */
    fun findByOrderNo(orderNo: String): Order? = orders.firstOrNull { it.orderNo == orderNo }

    /**
     * 按订单号取订单；库里没有就用这个订单号新建一个并入列。
     * 设备详情页的关联订单（含历史订单）点进来时，保证一定有实体可打开、可编辑。
     */
    fun getOrAddByOrderNo(orderNo: String): Order {
        orders.firstOrNull { it.orderNo == orderNo }?.let { return it }
        val created = Order(id = newId(), orderNo = orderNo, customer = "")
        orders.add(created)
        return created
    }

    /** 列表排序：进行中优先，同状态保持原有顺序（sortedBy 稳定排序） */
    fun sortedOrders(): List<Order> =
        orders.sortedBy { if (it.status == OrderStatus.IN_PROGRESS) 0 else 1 }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/** 记住一个订单存储实例 */
@Composable
fun rememberOrderStore(): OrderStore = remember { OrderStore() }

/** 时间戳：精确到年月日时分（yyyy-MM-dd HH:mm） */
private fun timeStamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

/**
 * 生成提单时间：精确到年月日时分（yyyy-MM-dd HH:mm）。
 * 保存订单时调用，记录该订单的提单时间，后续业务使用。
 */
fun currentSubmitTime(): String = timeStamp()

/** 生成节点的开始 / 结束时间（与提单时间同一格式） */
fun currentNodeTime(): String = timeStamp()

/**
 * 把开始测试时填写的预计小时数换算成「X天Y小时」：25→「1天1小时」、24→「1天」、
 * 3→「3小时」。非法（非正整数）返回空串，基础信息里对应行自然隐藏。
 */
fun formatTestDuration(hours: String): String {
    val h = hours.trim().toIntOrNull() ?: return ""
    if (h <= 0) return ""
    val days = h / 24
    val rem = h % 24
    return when {
        days > 0 && rem > 0 -> "${days}天${rem}小时"
        days > 0 -> "${days}天"
        else -> "${rem}小时"
    }
}

/**
 * 预计结束时间 = 节点开始时间 + 预计测试小时数；任一非法返回空串。
 * 与节点时间同一 yyyy-MM-dd HH:mm 格式，供测试进行中的节点行展示。
 */
fun expectedEndTime(startTime: String, hours: String): String {
    val h = hours.trim().toIntOrNull() ?: return ""
    if (h <= 0) return ""
    if (startTime.isBlank()) return ""
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val start = runCatching { fmt.parse(startTime) }.getOrNull() ?: return ""
    return fmt.format(Date(start.time + h * 3600_000L))
}
