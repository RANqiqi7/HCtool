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
 * @param status        订单状态
 * @param submitTime    提单时间（保存时由后端记录，精确到年月日时分，格式 yyyy-MM-dd HH:mm）
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
    val status: OrderStatus = OrderStatus.IN_PROGRESS,
    val submitTime: String = "",
)

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
            status = OrderStatus.IN_PROGRESS, submitTime = "2026-10-23 10:02"
        ),
        Order(
            newId(), "DD-241022", "谱尼测试",
            sampleName = "土壤样", sampleCount = "6", sampleNo = "P2410-C",
            testCondition = "低温冷藏", caseOwner = "李四",
            status = OrderStatus.IN_PROGRESS, submitTime = "2026-10-22 09:40"
        ),
        Order(
            newId(), "DD-241021", "华测检测",
            sampleName = "空气样", sampleCount = "8", sampleNo = "P2410-A",
            testCondition = "常温常湿", caseOwner = "张三",
            status = OrderStatus.IN_PROGRESS, submitTime = "2026-10-21 14:15"
        ),
        Order(
            newId(), "DD-241018", "sgs 通标",
            sampleName = "食品样", sampleCount = "20", sampleNo = "P2410-B",
            testCondition = "恒温 40℃", caseOwner = "王五",
            status = OrderStatus.COMPLETED, submitTime = "2026-10-18 08:30"
        ),
        Order(
            newId(), "DD-241013", "谱尼测试",
            sampleName = "水质样", sampleCount = "10", sampleNo = "P2410-D",
            testCondition = "常温常湿", caseOwner = "李四",
            status = OrderStatus.COMPLETED, submitTime = "2026-10-13 16:20"
        ),
        Order(
            newId(), "DD-240930", "华测检测",
            sampleName = "油品样", sampleCount = "4", sampleNo = "P2409-X",
            testCondition = "高温 60℃", caseOwner = "赵六",
            status = OrderStatus.COMPLETED, submitTime = "2026-09-30 11:05"
        ),
    )

    /** 新增一个订单 */
    fun addOrder(order: Order) {
        orders.add(order)
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

/**
 * 生成提单时间：精确到年月日时分（yyyy-MM-dd HH:mm）。
 * 保存订单时调用，记录该订单的提单时间，后续业务使用。
 */
fun currentSubmitTime(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
