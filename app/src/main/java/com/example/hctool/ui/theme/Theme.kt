package com.example.hctool.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 应用主题：使用 miuix 的 MiuixTheme，呈现 HyperOS 风格
 *
 * @param darkTheme 是否为深色模式，默认跟随系统
 * @param content 主题内容
 */
@Composable
fun HCtoolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

    MiuixTheme(
        colors = colorScheme,
        content = content
    )
}
