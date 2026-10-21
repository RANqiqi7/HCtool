package com.example.hctool.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Tasks

/**
 * 导航栏标签页数据类
 *
 * @param title 标签文字
 * @param icon 标签图标（miuix 图标库）
 */
data class NavBarTab(
    val title: String,
    val icon: ImageVector
)

/**
 * 导航栏标签集合
 */
object NavBarTabs {
    /**
     * 默认的三个导航标签：设备、订单、待办
     */
    val defaultTabs = listOf(
        NavBarTab(title = "设备", icon = MiuixIcons.Regular.SearchDevice),
        NavBarTab(title = "订单", icon = MiuixIcons.Regular.ListView),
        NavBarTab(title = "待办", icon = MiuixIcons.Regular.Tasks)
    )
}
