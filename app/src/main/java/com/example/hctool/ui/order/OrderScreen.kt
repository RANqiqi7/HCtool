package com.example.hctool.ui.order

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hctool.ui.components.frostedSurface
import com.example.hctool.ui.device.FilterMenu
import com.example.hctool.ui.device.ListHeader
import com.example.hctool.ui.device.pressable
import com.example.hctool.ui.device.rememberImageBitmap
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 订单筛选标签：与订单状态一一对应，label 为显示文本 */
enum class OrderFilterTag(val status: OrderStatus, val label: String) {
    IN_PROGRESS(OrderStatus.IN_PROGRESS, "进行中"),
    COMPLETED(OrderStatus.COMPLETED, "已完结"),
}

/**
 * 订单管理页（pager 第 1 页）：顶部「订单列表」磨砂副标题栏 + 两列瀑布流订单卡片。
 * 副标题栏与设备页共用 ListHeader，因此高度、内边距与右侧筛选按钮完全一致；
 * 筛选面板同样共用 FilterMenu，标签为「进行中」「已完结」，挂在根 Box 上叠加弹出，
 * 副标题栏在屏幕上半部分时向下展开、反之向上，边框不越过悬浮顶栏与底栏。
 * 卡片主标题 = 订单号，副标题 = 订单状态（进行中 / 已完结），
 * 下方与设备卡片一样留有订单图片展示位；进行中订单优先排在前面。
 *
 * @param store        订单数据（提升到 pager 外层持有）
 * @param backdrop     玻璃采样源（用 imageBackdrop，只采背景图）
 * @param inPreview    预览态退化玻璃为纯色
 * @param onOrderClick 点击某张订单卡片（由 MainActivity 弹出订单详情页）
 */
@Composable
fun OrderScreen(
    store: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    onOrderClick: (Order) -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current

    // 筛选：null = 不筛选（显示全部订单）
    var activeFilter by remember { mutableStateOf<OrderFilterTag?>(null) }
    // 筛选下拉列表开关
    var filterMenuOpen by remember { mutableStateOf(false) }
    // 记录副标题栏顶 / 底边在整页中的 y 坐标，用于判断展开方向与钉位
    var headerTop by remember { mutableStateOf(0.dp) }
    var headerBottom by remember { mutableStateOf(0.dp) }
    // 记录整页高度，用于判断上 / 下半部分与列表可用最大高度
    var rootH by remember { mutableStateOf(0.dp) }

    val filterGroups = listOf(OrderFilterTag.entries.map { it.label })
    // 共享筛选面板用「组下标 + 项下标」标记选中项
    val activeIndex = activeFilter?.let { 0 to OrderFilterTag.entries.indexOf(it) }

    val shownOrders = activeFilter?.let { tag ->
        store.sortedOrders().filter { it.status == tag.status }
    } ?: store.sortedOrders()

    // 展开方向：副标题栏中心在屏幕上半部分 → 向下展开，反之向上
    val headerCenter = (headerTop + headerBottom) / 2
    val expandDown = headerCenter <= rootH / 2
    // 筛选列表边框的上下界限：不越过悬浮顶栏与底栏（与设备页同基准）
    val topLimit = statusTop + 90.dp
    val bottomLimit = rootH - navBottom - 120.dp
    val panelMaxH = (if (expandDown) bottomLimit - headerBottom - 6.dp else headerTop - 6.dp - topLimit)
        .coerceAtLeast(120.dp)

    // 订单内容采样层：单独记录「副标题栏 + 订单列表」，供筛选列表玻璃采样；
    // 筛选列表与遮罩都是本层的兄弟节点，不会自采样导致崩溃
    val orderContentBackdrop = rememberLayerBackdrop { drawContent() }
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, orderContentBackdrop)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootH = with(density) { it.height.toDp() } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(orderContentBackdrop)
                .verticalScroll(rememberScrollState())
                // 顶部让开悬浮标题胶囊，底部让开悬浮底栏（与设备页同基准）
                .padding(top = statusTop + 90.dp)
                .padding(bottom = navBottom + 120.dp)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 副标题栏：磨砂圆角外框「订单列表」居左、框内右侧筛选按钮；
            // 筛选列表挂在根 Box 上叠加显示，不参与这里的高度
            Box(modifier = Modifier.fillMaxWidth()) {
                ListHeader(
                    backdrop = backdrop,
                    inPreview = inPreview,
                    title = "订单列表",
                    filterActive = activeFilter != null,
                    onToggleFilter = { filterMenuOpen = !filterMenuOpen },
                    onPositionChanged = { top, bottom ->
                        headerTop = top
                        headerBottom = bottom
                    }
                )
            }

            OrderMasonry(
                orders = shownOrders,
                filtered = activeFilter != null,
                backdrop = backdrop,
                inPreview = inPreview,
                onOrderClick = onOrderClick
            )
        }

        // 筛选列表展开时铺一层透明遮罩：点列表外任意处平滑收起
        if (filterMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { filterMenuOpen = false }
            )
        }

        // 筛选列表：挂在根 Box 上叠加显示，不挤压下方卡片；
        // 出现 / 关闭都是纯高度拉伸，方向与钉位同向。
        // 用全限定名调顶层 AnimatedVisibility：本页外层是 Column，
        // 导入后会优先匹配到 ColumnScope 的重载而报隐式接收者错误
        androidx.compose.animation.AnimatedVisibility(
            visible = filterMenuOpen,
            enter = expandVertically(
                expandFrom = if (expandDown) Alignment.Top else Alignment.Bottom,
                animationSpec = tween(220)
            ),
            exit = shrinkVertically(
                shrinkTowards = if (expandDown) Alignment.Top else Alignment.Bottom,
                animationSpec = tween(200)
            ),
            modifier = if (expandDown) {
                Modifier
                    .align(Alignment.TopEnd)
                    // 与 Column 的 20dp 水平内边距对齐，右边线跟筛选按钮齐平
                    .offset(x = -20.dp, y = headerBottom + 6.dp)
            } else {
                Modifier
                    .align(Alignment.BottomEnd)
                    // 向上展开：底边钉在副标题栏顶边上方 6dp
                    .offset(x = -20.dp, y = headerTop - 6.dp - rootH)
            }
        ) {
            FilterMenu(
                backdrop = combinedBackdrop,
                inPreview = inPreview,
                groups = filterGroups,
                active = activeIndex,
                maxHeight = panelMaxH,
                onPick = { _, index ->
                    val tag = OrderFilterTag.entries[index]
                    // 再点已选中标签 = 取消筛选
                    activeFilter = if (activeFilter == tag) null else tag
                    filterMenuOpen = false
                }
            )
        }
    }
}

/**
 * 单张订单卡片：
 * - 主标题 = 订单号（不带状态颜色点）
 * - 副标题 = 订单状态（进行中蓝 / 已完结绿）
 * - 有图片则展示图片，无图片则不展示（与设备卡片同款图片位）
 */
@Composable
private fun OrderCard(
    order: Order,
    backdrop: Backdrop,
    inPreview: Boolean,
    onOrderClick: (Order) -> Unit,
) {
    val bitmap = rememberImageBitmap(order.imageUri)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 按压反馈需在磨砂绘制之前，缩放才能带动玻璃背景一起收缩
            .pressable(onClick = { onOrderClick(order) })
            .frostedSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(22.dp),
                inPreview = inPreview
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = order.orderNo,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = order.status.label(),
            style = MiuixTheme.textStyles.body2,
            color = order.status.color(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // 订单图片放在状态下方，高度随图片宽高比自适应（不同卡片高矮错落）
        if (bitmap != null) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            Image(
                bitmap = bitmap,
                contentDescription = order.orderNo,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * 订单列表：两列瀑布流（左右交替分配，卡片高度因有无图片而错落），与设备列表一致。
 *
 * @param filtered 是否处于筛选状态：决定空列表时的提示文案
 */
@Composable
private fun OrderMasonry(
    orders: List<Order>,
    filtered: Boolean,
    backdrop: Backdrop,
    inPreview: Boolean,
    onOrderClick: (Order) -> Unit,
) {
    if (orders.isEmpty()) {
        Text(
            text = if (filtered) "没有符合条件的订单" else "暂无订单，点击底栏加号新增订单",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        return
    }
    val left = orders.filterIndexed { index, _ -> index % 2 == 0 }
    val right = orders.filterIndexed { index, _ -> index % 2 == 1 }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            left.forEach { OrderCard(it, backdrop, inPreview, onOrderClick) }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            right.forEach { OrderCard(it, backdrop, inPreview, onOrderClick) }
        }
    }
}
