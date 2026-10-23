package com.example.hctool.ui.order

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
 */
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
 */
class OrderStore {
    /** 预置一批测试订单，覆盖两种状态；列表展示时进行中优先 */
    val orders = mutableStateListOf(
        Order(
            newId(), "DD-241023", "华测检测",
            sampleName = "饮用水样", sampleCount = "12", sampleNo = "P2410-E",
            testCondition = "常温常湿", caseOwner = "张三",
            submitTime = "2026-10-23 10:02",
            // IQC 已完成、测试进行中、OQC 未开始 → 节点 33%
            nodes = listOf(
                OrderNode("IQC", "2026-10-23 10:20", "2026-10-23 15:40"),
                OrderNode("测试", "2026-10-24 09:05"),
                OrderNode("OQC"),
            ),
            // 使用记录表已填、runcard 未填 → 表单 50%
            forms = listOf(
                OrderForm("使用记录表", "2026-10-23 16:02"),
                OrderForm("runcard"),
            )
        ),
        Order(
            newId(), "DD-241022", "谱尼测试",
            sampleName = "土壤样", sampleCount = "6", sampleNo = "P2410-C",
            testCondition = "低温冷藏", caseOwner = "李四",
            submitTime = "2026-10-22 09:40",
            // 仅 IQC 进行中
            nodes = listOf(
                OrderNode("IQC", "2026-10-22 11:00"),
                OrderNode("测试"),
                OrderNode("OQC"),
            )
        ),
        Order(
            newId(), "DD-241021", "华测检测",
            sampleName = "空气样", sampleCount = "8", sampleNo = "P2410-A",
            testCondition = "常温常湿", caseOwner = "张三",
            submitTime = "2026-10-21 14:15",
            // IQC 已开始：设备关联订单的「开始时间」定向到这里的 IQC 开始时间
            nodes = listOf(
                OrderNode("IQC", "2026-10-21 09:00"),
                OrderNode("测试"),
                OrderNode("OQC"),
            )
        ),
        Order(
            newId(), "DD-241020", "谱尼测试",
            sampleName = "环境样", sampleCount = "5", sampleNo = "P2410-D",
            testCondition = "常温常湿", caseOwner = "李四",
            submitTime = "2026-10-20 08:50",
            // 测试进行中：一厅音箱因此判定为使用中
            nodes = listOf(
                OrderNode("IQC", "2026-10-20 09:20", "2026-10-20 13:00"),
                OrderNode("测试", "2026-10-20 14:00"),
                OrderNode("OQC"),
            )
        ),
        Order(
            newId(), "DD-241019", "sgs 通标",
            sampleName = "试剂样", sampleCount = "9", sampleNo = "P2410-F",
            testCondition = "低温冷藏", caseOwner = "王五",
            submitTime = "2026-10-19 10:10",
            // IQC 进行中
            nodes = listOf(
                OrderNode("IQC", "2026-10-19 11:00"),
                OrderNode("测试"),
                OrderNode("OQC"),
            )
        ),
        Order(
            newId(), "DD-241018", "sgs 通标",
            sampleName = "食品样", sampleCount = "20", sampleNo = "P2410-B",
            testCondition = "恒温 40℃", caseOwner = "王五",
            submitTime = "2026-10-18 08:30",
            // 节点与表单都 100% → 推导为已完结，进度环走绿色
            nodes = listOf(
                OrderNode("IQC", "2026-10-18 09:00", "2026-10-18 12:30"),
                OrderNode("测试", "2026-10-18 13:10", "2026-10-19 16:45"),
                OrderNode("OQC", "2026-10-20 08:20", "2026-10-20 10:05"),
            ),
            forms = listOf(
                OrderForm("使用记录表", "2026-10-20 10:30"),
                OrderForm("runcard", "2026-10-20 11:00"),
            )
        ),
        Order(
            newId(), "DD-241013", "谱尼测试",
            sampleName = "水质样", sampleCount = "10", sampleNo = "P2410-D",
            testCondition = "常温常湿", caseOwner = "李四",
            submitTime = "2026-10-13 16:20",
            // 节点与表单都 100% → 已完结
            nodes = listOf(
                OrderNode("IQC", "2026-10-13 17:00", "2026-10-14 09:30"),
                OrderNode("测试", "2026-10-14 10:00", "2026-10-15 18:20"),
                OrderNode("OQC", "2026-10-16 08:40", "2026-10-16 11:15"),
            ),
            forms = listOf(
                OrderForm("使用记录表", "2026-10-16 11:40"),
                OrderForm("runcard", "2026-10-16 12:05"),
            )
        ),
        Order(
            newId(), "DD-240930", "华测检测",
            sampleName = "油品样", sampleCount = "4", sampleNo = "P2409-X",
            testCondition = "高温 60℃", caseOwner = "赵六",
            submitTime = "2026-09-30 11:05",
            // 节点与表单都 100% → 已完结
            nodes = listOf(
                OrderNode("IQC", "2026-09-30 11:30", "2026-09-30 15:00"),
                OrderNode("测试", "2026-10-01 09:00", "2026-10-02 17:30"),
                OrderNode("OQC", "2026-10-03 08:20", "2026-10-03 10:00"),
            ),
            forms = listOf(
                OrderForm("使用记录表", "2026-10-03 10:30"),
                OrderForm("runcard", "2026-10-03 11:00"),
            )
        ),
    )

    /** 新增一个订单 */
    fun addOrder(order: Order) {
        orders.add(order)
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
