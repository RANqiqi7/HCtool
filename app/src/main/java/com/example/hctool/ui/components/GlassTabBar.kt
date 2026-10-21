package com.example.hctool.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
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
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * iOS 悬浮液态玻璃底栏：一条几乎横跨屏幕的玻璃胶囊，
 * 里面浮着一个可拖拽的玻璃滑块，滑块盖在胶囊与图标之上并折射它们。
 *
 * 拖拽手感参照 DampedDragAnimation：
 * - 位置是一个带阻尼的弹簧值，松手时把手指速度作为初速度，落位会过冲再回弹；
 * - 按下时滑块等比放大到 78/56（约 1.39 倍）；
 * - 位移速度会转成横向拉伸 / 纵向压扁，快拖时滑块被"甩"长。
 *
 * 宽度由外部约束决定，各 tab 等分；高度固定 [barHeight]。
 *
 * @param backdrop 底栏玻璃的采样源（通常是页面内容层的 layerBackdrop）
 * @param items 标签文字，同时作为图标的无障碍描述
 * @param icons 与 [items] 一一对应的图标
 * @param selectedIndex 当前选中的下标
 * @param onSelected 选中 / 拖拽切换后的回调
 * @param pageProgress 外部分页容器的当前进度（第几页，可为小数）。
 * 传入后滑块会直接跟着左右的翻页连续移动，而不是等翻页结束才跳格
 */
// 位置弹簧参数：stiffness 决定落位快慢，dampingRatio 小于 1 才会过冲回弹
private const val SPRING_STIFFNESS = 700f
private const val SPRING_DAMPING_RATIO = 0.72f
// 阻尼系数 c = 2 ζ √k
private val SPRING_DAMPING = 2f * SPRING_DAMPING_RATIO * sqrt(SPRING_STIFFNESS)

@Composable
fun GlassTabBar(
    backdrop: Backdrop,
    items: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pageProgress: (() -> Float)? = null
) {
    val barHeight = 64.dp
    // 滑块到胶囊四周的间距，四个方向必须一致
    val thumbPadding = 4.dp
    // 不能再额外留白，否则滑块左右间距会比上下大
    val sidePadding = 0.dp
    // 按下时的放大倍数：56dp 的滑块放大到 78dp
    val pressedScale = 78f / 56f

    // 宽度跟随外部约束等分，方便做成 iOS 那种横跨屏幕的悬浮长条
    BoxWithConstraints(modifier = modifier.height(barHeight)) {
        val itemWidth = maxWidth / items.size
        val thumbWidth = itemWidth - thumbPadding * 2
        val thumbHeight = barHeight - thumbPadding * 2
        // 连续曲率胶囊，比 RoundedCornerShape 更接近 iOS 的圆角过渡
        val barShape = Capsule()
        // Compose Preview（layoutlib）不支持 RenderEffect，预览里把玻璃退化成纯色
        val inPreview = LocalInspectionMode.current

        val density = LocalDensity.current
        val itemWidthPx = with(density) { itemWidth.toPx() }

        // 用 layerBackdrop 记录「胶囊 + 图标」的绘制结果，滑块采样它就能透出图标
        val barBackdrop = rememberLayerBackdrop()
        val thumbBackdrop = rememberCombinedBackdrop(backdrop, barBackdrop)

        // 滑块位置，单位是"第几个 tab"，允许小数即 tab 之间的过渡
        var position by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
        // 位移速度（tab/秒）：拖动时跟手更新，松手后交给弹簧继续演化
        var velocity by remember { mutableFloatStateOf(0f) }
        // 弹簧的目标位置，以及是否正在落位
        var springTarget by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
        var settling by remember { mutableStateOf(false) }
        // 是否正在拖拽滑块：拖动时外部进度不能抢位置
        var isDragging by remember { mutableStateOf(false) }

        // 外部翻页进度：左右滑页时滑块跟着连续移动
        LaunchedEffect(Unit) {
            val progress = pageProgress ?: return@LaunchedEffect
            snapshotFlow { progress() }.collect { value ->
                if (!isDragging && !settling) {
                    position = value
                    springTarget = value
                }
            }
        }

        // 按下进度，驱动放大 / 高光 / 折射 / 图标放大
        val press = remember { Animatable(0f) }
        var isPressed by remember { mutableStateOf(false) }

        // 点按切换 / 外部改动选中项：交给弹簧滑到新位置。
        // 接了分页容器时位置完全由它驱动，这里不插手
        LaunchedEffect(selectedIndex) {
            if (pageProgress != null) return@LaunchedEffect
            val goal = selectedIndex.toFloat()
            if (!settling && position != goal) {
                springTarget = goal
                settling = true
            }
        }

        // 按下鼓起、松手回落，都用弹簧让反馈有肉感
        LaunchedEffect(isPressed) {
            press.animateTo(
                targetValue = if (isPressed) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f)
            )
        }

        // 阻尼弹簧：按 1/120s 固定步长做半隐式积分，掉帧也不会发散。
        // dampingRatio < 1 意味着到达目标后会过冲一点再回弹，这就是"阻尼感"的来源
        LaunchedEffect(settling) {
            if (!settling) return@LaunchedEffect
            var lastFrame = 0L
            while (settling) {
                withFrameNanos { frameTime ->
                    if (lastFrame == 0L) {
                        lastFrame = frameTime
                    } else {
                        var remaining =
                            ((frameTime - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        lastFrame = frameTime
                        while (remaining > 0f) {
                            val dt = minOf(remaining, 1f / 120f)
                            remaining -= dt
                            val accel = -SPRING_STIFFNESS * (position - springTarget) -
                                SPRING_DAMPING * velocity
                            velocity += accel * dt
                            position += velocity * dt
                        }
                        if (abs(position - springTarget) < 0.0005f && abs(velocity) < 0.005f) {
                            position = springTarget
                            velocity = 0f
                            settling = false
                        }
                    }
                }
            }
        }

        val activeProgress = press.value
        // 滑块表面的中性色：亮主题叠黑、暗主题叠白，静止时靠它让位置可辨
        val neutralTint = if (MiuixTheme.colorScheme.onBackground.luminance() < 0.5f) {
            Color.Black.copy(alpha = 0.14f)
        } else {
            Color.White.copy(alpha = 0.14f)
        }
        val surfaceAlpha = 0.28f * activeProgress
        val contrast = 1f + 0.15f * activeProgress
        val saturation = 1.2f + 0.7f * activeProgress
        // 静止只留一丝折射，按下才把玻璃质感推出来
        val lensHeight = (8f + 12f * activeProgress).dp
        val lensAmount = (14f + 26f * activeProgress).dp
        // 按下时图标微放大，跟滑块一起"鼓起"
        val iconScale = 1f + 0.15f * activeProgress

        // 外层只负责布局与手势：不带 drawBackdrop，避免它的 clip 把放大后的滑块裁掉
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 按下时整条玻璃轻微收缩，做出 iOS 那种挤压反馈
                .graphicsLayer {
                    val shrink = 1f - 0.02f * activeProgress
                    scaleX = shrink
                    scaleY = shrink
                }
                // 一个手势同时负责按下反馈、横向拖拽、松手吸附
                .pointerInput(itemWidthPx, items.lastIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        // 抓住正在回弹的滑块：中断弹簧，从这里接着拖
                        settling = false
                        var dragging = false
                        var travel = 0f
                        var lastX = down.position.x
                        var lastTimeMs = down.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - lastX
                            lastX = change.position.x
                            // 用事件时间差算瞬时速度；手指不动时它会自然衰减到 0
                            val dtMs = (change.uptimeMillis - lastTimeMs).coerceAtLeast(1L)
                            lastTimeMs = change.uptimeMillis
                            if (!dragging) {
                                travel += dx
                                // 超过触摸阈值才认定是拖拽，否则留给图标的 clickable 当点击
                                if (abs(travel) > viewConfiguration.touchSlop) {
                                    dragging = true
                                    isDragging = true
                                }
                            }
                            if (dragging) {
                                // 消费掉事件，让底下图标的 clickable 放弃这次点击
                                change.consume()
                                // 位置跟手；中心被夹在首尾 tab 之间，不会滑出 tab 框
                                position = (position + dx / itemWidthPx)
                                    .coerceIn(0f, items.lastIndex.toFloat())
                                // 手指速度换算成"每秒几个 tab"，平滑一下同时供拉伸变形使用
                                val instant = (dx / itemWidthPx) / (dtMs / 1000f)
                                velocity = lerp(velocity, instant.coerceIn(-8f, 8f), 0.5f)
                            }
                        }
                        isPressed = false
                        isDragging = false
                        if (dragging) {
                            // 松手：把手指速度当弹簧初速度，落位会先冲过头再回弹
                            val target = position.roundToInt().coerceIn(0, items.lastIndex)
                            springTarget = target.toFloat()
                            settling = true
                            if (target != selectedIndex) onSelected(target)
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
                    .then(
                        if (inPreview) {
                            Modifier.background(
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                shape = barShape
                            )
                        } else {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { barShape },
                                effects = {
                                    // iOS 液态玻璃：轻微提鲜 + 磨砂 + 边缘明显折射
                                    vibrancy()
                                    blur(5.dp.toPx())
                                    lens(20.dp.toPx(), 36.dp.toPx())
                                },
                                // 边缘高光 + 大范围柔和外阴影，做出悬浮感
                                highlight = { Highlight.Default },
                                shadow = { Shadow(radius = 32.dp, color = Color.Black.copy(alpha = 0.2f)) }
                            )
                        }
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
                                modifier = Modifier
                                    .size(28.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
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
            // 采样 combinedBackdrop（页面背景 + 胶囊图标层），图标能透过玻璃被折射看到
            Box(
                modifier = Modifier
                    .offset(x = sidePadding + thumbPadding + itemWidth * position)
                    .width(thumbWidth)
                    .height(thumbHeight)
                    .then(
                        if (inPreview) {
                            // 预览里滑块用半透明白，静止位置也能看出来
                            Modifier.background(
                                color = Color.White.copy(alpha = 0.5f),
                                shape = Capsule()
                            )
                        } else {
                            Modifier.drawBackdrop(
                                backdrop = thumbBackdrop,
                                shape = { Capsule() },
                                // 高光、阴影、内阴影都乘按下进度：静止时滑块几乎只是一层淡色
                                highlight = { Highlight.Default.copy(alpha = activeProgress) },
                                shadow = { Shadow(alpha = activeProgress) },
                                innerShadow = {
                                    InnerShadow(radius = 8.dp * activeProgress, alpha = activeProgress)
                                },
                                effects = {
                                    colorControls(contrast = contrast, saturation = saturation)
                                    lens(
                                        refractionHeight = lensHeight.toPx(),
                                        refractionAmount = lensAmount.toPx(),
                                        chromaticAberration = true
                                    )
                                },
                                // 缩放走绘制层：不参与布局，放大后可以自然溢出胶囊与父约束
                                layerBlock = {
                                    val scale = 1f + (pressedScale - 1f) * activeProgress
                                    val v = velocity / 10f
                                    // 往哪边甩就把哪边拉长、另一边压扁，甩得越快越明显
                                    scaleX = scale / (1f - (v * 0.75f).coerceIn(-0.2f, 0.2f))
                                    scaleY = scale * (1f - (v * 0.25f).coerceIn(-0.2f, 0.2f))
                                },
                                onDrawSurface = {
                                    // 静止：只留一层很淡的中性色，让选中位置可辨；按下时退到一半
                                    drawRect(neutralTint, alpha = 1f - 0.5f * activeProgress)
                                    // 按下：叠一层白，把玻璃厚度提起来
                                    drawRect(Color.White.copy(alpha = surfaceAlpha))
                                }
                            )
                        }
                    )
            )
        }
    }
}
