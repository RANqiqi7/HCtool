package com.example.hctool.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 应用主题：使用 miuix 的 MiuixTheme，呈现 HyperOS 风格。
 *
 * 深浅色由 [LocalAppSettings] 的主题模式决定（跟随系统 / 浅色 / 深色），
 * 在设置页切换后立即生效并持久化。深色沿用白色字体那套配色（darkColorScheme），
 * 浅色为深色字体（lightColorScheme）；磨砂卡片会按当前明暗自适应叠色，保证文字可读。
 */
@Composable
fun HCtoolTheme(content: @Composable () -> Unit) {
    val mode = LocalAppSettings.current.themeMode
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MiuixTheme(
        colors = if (dark) darkColorScheme() else lightColorScheme(),
        content = content
    )
}
