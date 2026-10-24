package com.example.hctool.ui.theme

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme

/**
 * 应用主题：使用 miuix 的 MiuixTheme，呈现 HyperOS 风格
 *
 * 已去掉深色模式：不再跟随系统深浅色，固定用白色字体的那套配色
 * （深浅色原本就是反的，白色字体的 darkColorScheme 作为本应用唯一外观）。
 * 系统弹框（相册选择器、生物验证等）也固定走浅色主题，见 values/themes.xml。
 */
@Composable
fun HCtoolTheme(content: @Composable () -> Unit) {
    MiuixTheme(
        colors = darkColorScheme(),
        content = content
    )
}
