package com.example.hctool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.hctool.ui.components.NavBarTabs
import com.example.hctool.ui.theme.HCtoolTheme
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HCtoolTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // 根据选中的标签显示不同内容
    val contentText = when (selectedTabIndex) {
        0 -> "设备管理页面"
        1 -> "订单列表页面"
        2 -> "待办事项页面"
        else -> "未知页面"
    }

    Scaffold(
        topBar = {
            // miuix 顶部标题栏
            SmallTopAppBar(title = "HCtool")
        },
        bottomBar = {
            // miuix 底部导航栏
            NavigationBar {
                NavBarTabs.defaultTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = index == selectedTabIndex,
                        onClick = { selectedTabIndex = index },
                        icon = tab.icon,
                        label = tab.title
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contentText,
                style = MiuixTheme.textStyles.title1,
                color = MiuixTheme.colorScheme.onBackground
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    HCtoolTheme {
        MainScreen()
    }
}
