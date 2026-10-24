package com.example.hctool.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.example.hctool.ui.theme.isInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 弹窗打开时冻结整块区域的点击：在 Initial pass（父先于子分发）把指针事件全部消费，
 * 子级 clickable 永远拿不到未消费的按下，整块区域等效不可交互。
 * 用于顶栏 / 底栏这类绘制在弹窗遮罩之上的悬浮层：
 * 弹窗存在时它们虽然被遮罩盖住但仍可点，挂上这个修饰符即可屏蔽。
 *
 * @param blocked 为 true 时拦截并消费所有触摸；false 时不做任何事
 */
fun Modifier.blockClicks(blocked: Boolean): Modifier = then(
    if (blocked) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    } else {
        Modifier
    }
)

/**
 * 全局统一的液态玻璃材质（以右下角添加键 GlassFab 为模板）。
 *
 * 这是整个项目玻璃质感的唯一出处：以后任何需要"玻璃"的组件都调用它，
 * 材质参数只在这一处定义，改这里即可全局生效，避免各处复制 drawBackdrop 导致参数漂移。
 *
 * @param backdrop     玻璃采样源（顶层浮层用 imageBackdrop，底栏/FAB 用 backdrop）
 * @param shape        玻璃形状，默认胶囊；会向下展开的面板传 RoundedCornerShape(32.dp)
 * @param inPreview    预览态（layoutlib 不支持 RenderEffect）退化为纯色
 * @param surfaceTint  叠加在玻璃表面的颜色，默认透明（与 FAB 一致的纯净玻璃）
 * @param previewTint  预览态退化时使用的纯色
 */
fun Modifier.glassSurface(
    backdrop: Backdrop,
    shape: Shape = Capsule(),
    inPreview: Boolean = false,
    surfaceTint: Color = Color.Transparent,
    previewTint: Color = Color.White.copy(alpha = 0.12f),
): Modifier = this.then(
    if (inPreview) {
        Modifier.background(color = previewTint, shape = shape)
    } else {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // 与底栏完全同一套参数，保证所有玻璃看起来是同一材质
                vibrancy()
                blur(5.dp.toPx())
                lens(20.dp.toPx(), 36.dp.toPx())
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(radius = 32.dp, color = Color.Black.copy(alpha = 0.2f)) },
            onDrawSurface = { drawRect(surfaceTint) }
        )
    }
)

/** 磨砂玻璃的表面叠色：一层较深的遮罩，保证白色文字在亮背景上也清晰 */
val FrostedTint = Color.Black.copy(alpha = 0.45f)

/** 浅色模式下的磨砂叠色：换成较实的白色遮罩，保证深色文字同样清晰 */
val LightFrostedTint = Color.White.copy(alpha = 0.62f)

/**
 * 磨砂玻璃材质：比 [glassSurface] 模糊更强、并叠一层遮罩，
 * 用于内容卡片 / 弹窗等需要高可读性的容器（顶栏 / 底栏仍用通透的 [glassSurface]）。
 * 默认叠色按当前明暗自适应：深色用黑遮罩、浅色用白遮罩，保证文字始终可读。
 */
@Composable
fun Modifier.frostedSurface(
    backdrop: Backdrop,
    shape: Shape = RoundedCornerShape(28.dp),
    inPreview: Boolean = false,
    surfaceTint: Color? = null,
    previewTint: Color? = null,
): Modifier {
    val dark = isInDarkTheme()
    val tint = surfaceTint ?: if (dark) FrostedTint else LightFrostedTint
    val preview = previewTint ?: if (dark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.7f)
    return this.then(
        if (inPreview) {
            Modifier.background(color = preview, shape = shape)
        } else {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    // 强模糊：把背后内容磨成雾面，突出前景文字
                    blur(40.dp.toPx())
                    lens(8.dp.toPx(), 16.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = 32.dp, color = Color.Black.copy(alpha = 0.2f)) },
                onDrawSurface = { drawRect(tint) }
            )
        }
    )
}

/** 弹窗专用叠色：比卡片更实的深色，压住背后亮壁纸的透光，让弹窗读起来是一整块实体 */
val DialogTint = Color.Black.copy(alpha = 0.62f)

/** 浅色模式下的弹窗叠色 */
val LightDialogTint = Color.White.copy(alpha = 0.8f)

/**
 * 弹窗专用表面：更实的磨砂 + 一圈亮边高光。
 * 亮边给弹窗一道清晰的轮廓光，配合遮罩让弹窗从背景中跳出来（更突出）。
 * 叠色与描边都按当前明暗自适应。
 */
@Composable
fun Modifier.dialogSurface(
    backdrop: Backdrop,
    inPreview: Boolean = false,
    shape: Shape = RoundedCornerShape(28.dp),
): Modifier = this
    .frostedSurface(
        backdrop = backdrop,
        shape = shape,
        inPreview = inPreview,
        surfaceTint = if (isInDarkTheme()) DialogTint else LightDialogTint,
        previewTint = if (isInDarkTheme()) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.88f)
    )
    .border(
        width = 1.2.dp,
        color = if (isInDarkTheme()) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.12f),
        shape = shape
    )

/**
 * 统一的玻璃按钮：玻璃材质 + 按压缩放反馈（按下缩到 0.94，spring 0.65/900 弹回），
 * 与右下角添加键手感完全一致。以后新增任何可点击的玻璃组件都优先用它。
 *
 * @param backdrop      玻璃采样源
 * @param onClick       点击回调
 * @param modifier      外部修饰符（尺寸、对齐等放这里）
 * @param shape         玻璃形状，默认胶囊
 * @param surfaceTint   表面叠色，默认透明
 * @param pressedScale  按下缩放比例，默认 0.94（与 FAB 一致）
 * @param contentAlignment 内容对齐方式
 * @param content       按钮内容
 */
@Composable
fun GlassButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = Capsule(),
    surfaceTint: Color = Color.Transparent,
    pressedScale: Float = 0.94f,
    frosted: Boolean = false,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val inPreview = LocalInspectionMode.current
    // 按压反馈：命名 interactionSource + collectIsPressedAsState 监听，弹簧过渡缩放
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "glassButtonPress"
    )
    // 材质可选：默认通透玻璃，frosted=true 用磨砂玻璃（放在磨砂容器上的小按钮用）
    val surfaceModifier = if (frosted) {
        Modifier.frostedSurface(backdrop = backdrop, shape = shape, inPreview = inPreview)
    } else {
        Modifier.glassSurface(
            backdrop = backdrop,
            shape = shape,
            inPreview = inPreview,
            surfaceTint = surfaceTint
        )
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(surfaceModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = contentAlignment,
        content = content
    )
}
