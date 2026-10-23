package com.example.hctool.ui.order

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hctool.ui.components.frostedSurface
import com.example.hctool.ui.device.pressable
import com.example.hctool.ui.device.rememberImageBitmap
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 订单管理页（pager 第 1 页）：照抄设备页的设备列表——
 * 顶部「订单列表」磨砂副标题栏 + 两列瀑布流订单卡片。
 * 卡片主标题 = 订单号（不带状态颜色点），副标题 = 订单状态（进行中 / 已完结），
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 顶部让开悬浮标题胶囊，底部让开悬浮底栏（与设备页同基准）
            .padding(top = statusTop + 90.dp)
            .padding(bottom = navBottom + 120.dp)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OrderListHeader(backdrop = backdrop, inPreview = inPreview)
        OrderMasonry(
            orders = store.sortedOrders(),
            backdrop = backdrop,
            inPreview = inPreview,
            onOrderClick = onOrderClick
        )
    }
}

/**
 * 「订单列表」副标题栏：磨砂玻璃圆角外框、文字居左，与设备页副标题栏同款。
 */
@Composable
private fun OrderListHeader(
    backdrop: Backdrop,
    inPreview: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .frostedSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(22.dp),
                inPreview = inPreview
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "订单列表",
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 订单列表：两列瀑布流（左右交替分配，卡片高度因有无图片而错落），与设备列表一致。
 */
@Composable
private fun OrderMasonry(
    orders: List<Order>,
    backdrop: Backdrop,
    inPreview: Boolean,
    onOrderClick: (Order) -> Unit,
) {
    if (orders.isEmpty()) {
        Text(
            text = "暂无订单，点击底栏加号新增订单",
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
