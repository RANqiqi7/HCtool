package com.example.hctool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hctool.R
import com.example.hctool.ui.components.GlassTabBar
import com.example.hctool.ui.theme.HCtoolTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Tasks
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
    // 当前选中的标签下标，赋值后会触发重组刷新界面
    var selectedIndex by remember { mutableStateOf(0) }
    // 标签文字与图标保持不变（两个列表按下标一一对应）
    val items = listOf("设备", "订单", "待办")
    val icons = listOf(
        MiuixIcons.Regular.SearchDevice,
        MiuixIcons.Regular.ListView,
        MiuixIcons.Regular.Tasks
    )

    // 根据选中的标签显示不同内容
    val contentText = when (selectedIndex) {
        0 -> "设备管理页面"
        1 -> "订单列表页面"
        2 -> "待办事项页面"
        else -> "未知页面"
    }

    // 底色：先铺一层纯色，避免采样区域外出现透明像素
    val backgroundColor = Color.White
    // 模糊采样源：记录底色 + 底栏后方的内容，供玻璃绘制时取用
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 内容层：作为底栏的模糊来源（layerBackdrop 必须放在内容根节点上）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            // 背景图：铺满内容区，同时作为底栏玻璃的采样源
            Image(
                painter = painterResource(R.drawable.bg_main),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // miuix 页面骨架：负责顶栏与内容区的布局、内边距分配
            Scaffold(
                // 必须透明，否则 miuix 的 surface 会盖住背景图
                containerColor = Color.Transparent,
                topBar = {
                    // miuix 顶部标题栏；SmallTopAppBar 本身没有分割线参数，需在下方手动追加
                    Column {
                        SmallTopAppBar(
                            title = "HCtool",
                            // 顶栏也透明，让背景图透上来
                            color = Color.Transparent
                        )
                        HorizontalDivider()
                    }
                }
            ) { innerPadding ->
                // content 槽位必须消费 innerPadding，否则内容会被顶栏遮挡
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    // 使用 miuix 主题的标题样式与主题色，深色模式下自动适配
                    Text(
                        text = contentText,
                        style = MiuixTheme.textStyles.title1,
                        color = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // 玻璃滑块底栏：拖滑块或点图标都能切换 tab
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                // 避开系统手势导航条
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassTabBar(
                backdrop = backdrop,
                items = items,
                icons = icons,
                selectedIndex = selectedIndex,
                onSelected = { selectedIndex = it }
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
