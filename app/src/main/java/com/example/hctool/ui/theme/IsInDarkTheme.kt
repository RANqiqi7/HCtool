package com.example.hctool.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun isInDarkTheme(): Boolean =
    MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
