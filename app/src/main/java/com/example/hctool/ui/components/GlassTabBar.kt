package com.example.hctool.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液态玻璃底部导航栏：内部带一个可拖拽的玻璃滑块，用来滑动切换 tab。
 *
 * @param backdrop 底栏玻璃的采样源（通常是页面内容层的 layerBackdrop）
 * @param items 标签文字，同时作为图标的无障碍描述
 * @param icons 与 [items] 一一对应的图标
 * @param selectedIndex 当前选中的下标
 * @param onSelected 选中 / 拖拽切换后的回调
 */
@Composable
fun GlassTabBar(
    backdrop: Backdrop,
    items: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemWidth = 76.dp
    val barHeight = 64.dp
    // 滑块到胶囊四周的间距，四个方向必须一致
    val thumbPadding = 4.dp
    // 不能再额外留白，否则滑块左右间距会比上下大
    val sidePadding = 0.dp
    val barWidth = itemWidth * items.size + sidePadding * 2
    val thumbWidth = itemWidth - thumbPadding * 2
    val thumbHeight = barHeight - thumbPadding * 2
    val barShape = RoundedCornerShape(barHeight / 2)

    val density = LocalDensity.current
    val itemWidthPx = with(density) { itemWidth.toPx() }

    // 用 layerBackdrop 记录「胶囊 + 图标」的绘制结果，滑块采样它就能透出图标
    val barBackdrop = rememberLayerBackdrop()
    val thumbBackdrop = rememberCombinedBackdrop(backdrop, barBackdrop)

    // 拖拽中的临时位移（px）与拖拽状态
    var dragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    // 按下状态：整条底栏任意位置按下都会触发。
    // 不能用 draggable 的 interactionSource，因为点在图标的 clickable 上时 down 会被消费掉
    var pressed by remember { mutableStateOf(false) }
    val active = pressed || dragging
    val activeProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "thumbActiveProgress"
    )

    // 静止 → 按压时的参数：放大、提高对比度与饱和度、加强折射
    // 外扩 14dp：胶囊内边距是 4dp，外扩 14dp 后四周各超出胶囊 10dp
    val expand = 14.dp * activeProgress
    val surfaceAlpha = 0.16f + 0.10f * activeProgress
    val contrast = 1f + 0.2f * activeProgress
    val saturation = 1.5f + 0.5f * activeProgress
    val lensHeight = (12f + 10f * activeProgress).dp
    val lensAmount = (20f + 26f * activeProgress).dp
    val currentThumbWidth = thumbWidth + expand * 2
    val currentThumbHeight = thumbHeight + expand * 2

    // 静止时滑块对齐选中项；拖拽时跟随手指，不做动画。
    // 中心被夹在首尾 tab 之间，不会滑出 tab 框
    val baseOffset = itemWidth * selectedIndex
    val maxOffset = itemWidth * (items.size - 1)
    val targetOffset = if (dragging) {
        (baseOffset + with(density) { dragOffsetPx.toDp() }).coerceIn(0.dp, maxOffset)
    } else {
        baseOffset
    }
    val thumbOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = if (dragging) tween(durationMillis = 0) else spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "glassThumbOffset"
    )

    // 外层只负责布局与手势：不带 drawBackdrop，避免它的 clip 把放大后的滑块裁掉
    Box(
        modifier = modifier
            .width(barWidth)
            .height(barHeight)
            // 横向拖拽：松手后按拖动距离吸附到最近的 tab
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragging = true
                    dragOffsetPx += delta
                },
                onDragStopped = {
                    // 用"夹在 tab 框内"后的位置算目标下标，避免越界
                    val baseOffsetPx = itemWidthPx * selectedIndex
                    val maxOffsetPx = itemWidthPx * (items.size - 1)
                    val settledPx = (baseOffsetPx + dragOffsetPx).coerceIn(0f, maxOffsetPx)
                    val target = (settledPx / itemWidthPx).roundToInt().coerceIn(0, items.lastIndex)
                    dragOffsetPx = 0f
                    dragging = false
                    onSelected(target)
                }
            )
            // 监听整条底栏的按下 / 松开，用来驱动滑块的放大与增强
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 第一层：底栏本体 + 图标（同一层，图标画在胶囊之上）。
        // 用 layerBackdrop 记录这一层的绘制结果，滑块采样它就能透出图标
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(barBackdrop)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { barShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(16.dp.toPx(), 32.dp.toPx())
                    },
                    // 边缘高光 + 外阴影（阴影加深一点，深色背景下才看得出来）
                    highlight = { Highlight.Default },
                    shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.22f)) }
                )
        ) {
            // 图标行
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = sidePadding),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            imageVector = icons[index],
                            contentDescription = label,
                            modifier = Modifier.size(28.dp),
                            colorFilter = ColorFilter.tint(
                                if (index == selectedIndex) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                                }
                            )
                        )
                    }
                }
            }
        }

        // 第二层：玻璃滑块，盖在胶囊与图标之上；
        // 采样 combinedBackdrop（页面背景 + 胶囊图标层），图标能透过玻璃被折射看到；
        // 放在外层节点里，且用 requiredSize 忽略父级高度约束，放大才能真的超出胶囊
        Box(
            modifier = Modifier
                .offset(x = sidePadding + thumbOffset + thumbPadding - expand)
                .requiredSize(currentThumbWidth, currentThumbHeight)
                .drawBackdrop(
                    backdrop = thumbBackdrop,
                    shape = { CircleShape },
                    effects = {
                        colorControls(contrast = contrast, saturation = saturation)
                        lens(
                            refractionHeight = lensHeight.toPx(),
                            refractionAmount = lensAmount.toPx(),
                            chromaticAberration = true
                        )
                    },
                    // 按压时表面更亮，滑块更"凸"
                    onDrawSurface = { drawRect(Color.White.copy(alpha = surfaceAlpha)) }
                )
        )
    }
}
