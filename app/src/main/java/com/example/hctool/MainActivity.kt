package com.example.hctool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hctool.R
import com.example.hctool.ui.components.GlassFab
import com.example.hctool.ui.components.GlassTabBar
import com.example.hctool.ui.theme.HCtoolTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch

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

    // 三个页面平铺，左右滑动切换
    val pageTexts = listOf("设备管理页面", "订单列表页面", "待办事项页面")
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()

    // 翻页 → 同步底栏高亮与顶栏标题
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { selectedIndex = it }
    }

    // 底色：先铺一层纯色，避免采样区域外出现透明像素
    val backgroundColor = Color.White
    // 模糊采样源：记录底色 + 底栏后方的内容，供玻璃绘制时取用
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    // 只含背景图的采样源：顶栏玻璃用它采样。
    // 顶栏自身就在页面层里，若直接采 page 层会把标题文字一起采进来，出现重影
    val imageBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    // 顶栏玻璃的表面色：浅色主题叠白、深色主题叠黑，决定"微透"的浓度
    val topBarTint = if (MiuixTheme.colorScheme.onBackground.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.28f)
    } else {
        Color.White.copy(alpha = 0.28f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 内容层：作为底栏的模糊来源（layerBackdrop 必须放在内容根节点上）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            // 背景图单独再记一层，作为顶栏玻璃的采样源
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(imageBackdrop)
            ) {
                Image(
                    painter = painterResource(R.drawable.bg_main),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // miuix 页面骨架：负责顶栏与内容区的布局、内边距分配
            Scaffold(
                // 必须透明，否则 miuix 的 surface 会盖住背景图
                containerColor = Color.Transparent,
                topBar = {
                    // 顶栏玻璃：只采背景图那一层，所以不会把标题自己折进去
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 离屏层：让下面的 DstOut 只在顶栏这块区域内擦除
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawBackdrop(
                                backdrop = imageBackdrop,
                                shape = { RectangleShape },
                                effects = {
                                    // 比底栏轻得多：只做磨砂 + 提鲜，不做边缘折射
                                    vibrancy()
                                    blur(8.dp.toPx())
                                },
                                // 关掉默认的边缘高光与外阴影：顶栏只要一层磨砂，
                                // 否则矩形底边那条高光看起来就像一条分割线
                                highlight = { null },
                                shadow = { null },
                                onDrawSurface = { drawRect(topBarTint) }
                            )
                            // 底边渐隐：用 DstOut 把最后一段擦成透明，取代原来的硬分割线
                            .drawWithContent {
                                drawContent()
                                val fade = 24.dp.toPx()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        1f to Color.Black,
                                        startY = size.height - fade,
                                        endY = size.height
                                    ),
                                    blendMode = BlendMode.DstOut
                                )
                            }
                    ) {
                        // 标题条：高度 52dp（与 miuix CollapsedHeight 一致），
                        // 自己铺一层是为了能对标题文字做淡入淡出
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 让玻璃连状态栏一起覆盖，标题本身下移到状态栏下方
                                .statusBarsPadding()
                                .height(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // 标题跟随底栏选中项：设备页显示"设备"，以此类推。
                            // 切换时旧标题淡出、新标题淡入
                            Crossfade(
                                targetState = selectedIndex,
                                animationSpec = tween(durationMillis = 220),
                                label = "topBarTitle"
                            ) { index ->
                                Text(
                                    text = items[index],
                                    style = MiuixTheme.textStyles.title3,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                // content 槽位必须消费 innerPadding，否则内容会被顶栏遮挡
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) { page ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // 使用 miuix 主题的标题样式与主题色，深色模式下自动适配
                        Text(
                            text = pageTexts[page],
                            style = MiuixTheme.textStyles.title1,
                            color = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // 底部悬浮层：左边是收缩后的玻璃底栏，右边是独立的玻璃加号按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                // 避开系统手势导航条
                .navigationBarsPadding()
                // iOS 悬浮风格：左右留白、离底边抬高一点
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassTabBar(
                    backdrop = backdrop,
                    items = items,
                    icons = icons,
                    selectedIndex = selectedIndex,
                    // 点图标 / 拖滑块 → 让分页容器滑到目标页
                    onSelected = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    // 不再铺满整行，把右侧空间让给 FAB
                    modifier = Modifier.weight(1f),
                    // 滑块位置直接跟着翻页进度连续移动
                    pageProgress = {
                        pagerState.currentPage + pagerState.currentPageOffsetFraction
                    }
                )
                // 添加按钮：与底栏同高同材质，视觉上属于同一条悬浮层
                GlassFab(
                    backdrop = backdrop,
                    icon = MiuixIcons.Regular.Add,
                    contentDescription = "添加"
                ) {
                    // 具体添加逻辑待接入
                }
            }
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
