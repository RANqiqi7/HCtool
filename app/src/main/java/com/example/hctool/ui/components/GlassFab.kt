package com.example.hctool.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 独立的玻璃圆形按钮，和底栏用同一套液态玻璃参数，
 * 所以两者在视觉上属于同一条悬浮层。
 *
 * 材质与按压反馈统一走 [GlassButton]（即全局玻璃模板），此处只负责尺寸与图标，
 * 保证和搜索键、标题框等所有玻璃组件外观/手感完全一致。
 *
 * @param backdrop 玻璃的采样源，通常与底栏传同一个
 * @param icon 按钮图标
 * @param contentDescription 图标的无障碍描述
 * @param modifier 外部修饰符
 * @param minus 为 true 时画成减号（miuix 图标集里没有纯减号字形，用一根圆角横条代替）
 * @param onClick 点击回调
 */
@Composable
fun GlassFab(
    backdrop: Backdrop,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    minus: Boolean = false,
    onClick: () -> Unit
) {
    GlassButton(
        backdrop = backdrop,
        onClick = onClick,
        // 尺寸与底栏等高，胶囊形状在正方形里就是正圆
        modifier = modifier.size(64.dp)
    ) {
        if (minus) {
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MiuixTheme.colorScheme.primary)
            )
        } else {
            Image(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary)
            )
        }
    }
}
