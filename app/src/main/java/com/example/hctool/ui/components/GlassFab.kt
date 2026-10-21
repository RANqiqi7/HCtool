package com.example.hctool.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 独立的玻璃圆形按钮，和底栏用同一套液态玻璃参数，
 * 所以两者在视觉上属于同一条悬浮层。
 *
 * @param backdrop 玻璃的采样源，通常与底栏传同一个
 * @param icon 按钮图标
 * @param contentDescription 图标的无障碍描述
 * @param modifier 外部修饰符
 * @param onClick 点击回调
 */
@Composable
fun GlassFab(
    backdrop: Backdrop,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // 按压反馈：和底栏滑块一样用弹簧过渡，松开后弹回
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "fabPress"
    )

    Box(
        modifier = modifier
            // 尺寸与底栏等高，胶囊形状在正方形里就是正圆
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    // 与底栏完全同一套参数，保证两块玻璃看起来是同一材质
                    vibrancy()
                    blur(5.dp.toPx())
                    lens(20.dp.toPx(), 36.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = 32.dp, color = Color.Black.copy(alpha = 0.2f)) }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary)
        )
    }
}
