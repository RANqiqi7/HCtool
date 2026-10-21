package com.example.hctool.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
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
    val sidePadding = 10.dp
    val thumbInset = 4.dp
    val barWidth = itemWidth * items.size + sidePadding * 2
    val barShape = RoundedCornerShape(barHeight / 2)

    val density = LocalDensity.current
    val itemWidthPx = with(density) { itemWidth.toPx() }

    // 拖拽中的临时位移（px）与拖拽状态
    var dragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    // 静止时滑块对齐选中项；拖拽时跟随手指，不做动画
    val baseOffset = itemWidth * selectedIndex
    val targetOffset = if (dragging) {
        baseOffset + with(density) { dragOffsetPx.toDp() }
    } else {
        baseOffset
    }
    val thumbOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = if (dragging) tween(durationMillis = 0) else spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "glassThumbOffset"
    )

    Box(
        modifier = modifier
            .width(barWidth)
            .height(barHeight)
            // 底栏本体：背景模糊 + 饱和度增强 + 边缘折射
            .drawBackdrop(
                backdrop = backdrop,
                shape = { barShape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                highlight = null
            )
            // 横向拖拽：松手后按拖动距离吸附到最近的 tab
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragging = true
                    dragOffsetPx += delta
                },
                onDragStopped = {
                    val moved = (dragOffsetPx / itemWidthPx).roundToInt()
                    val target = (selectedIndex + moved).coerceIn(0, items.lastIndex)
                    dragOffsetPx = 0f
                    dragging = false
                    onSelected(target)
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // 玻璃滑块：不模糊、只做折射，再叠一层极淡的白色高光，让它从底栏里“凸”出来
        Box(
            modifier = Modifier
                .offset(x = sidePadding + thumbOffset + thumbInset / 2)
                .width(itemWidth - thumbInset * 2)
                .height(barHeight - thumbInset * 2)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        lens(
                            refractionHeight = 12.dp.toPx(),
                            refractionAmount = 20.dp.toPx(),
                            chromaticAberration = true
                        )
                    },
                    // 淡淡的高光，保证滑块在玻璃底栏上依然可见
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.16f)) }
                )
        )

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
}
