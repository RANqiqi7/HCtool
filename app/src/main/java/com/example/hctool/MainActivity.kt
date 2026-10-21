package com.example.hctool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
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
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Search
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

    // 悬浮标题框的表面色：浅色主题叠白、深色主题叠黑，决定"微透"的浓度
    val topBarTint = if (MiuixTheme.colorScheme.onBackground.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.28f)
    } else {
        Color.White.copy(alpha = 0.28f)
    }

    // Compose Preview（layoutlib）不支持 RenderEffect，也不适合解码大图：
    // 预览里把玻璃退化成纯色块并跳过背景图，保证实时渲染能出图，真机不受影响
    val inPreview = LocalInspectionMode.current

    // 状态栏那一条的叠色：比标题框淡得多，只求图标能看清而不抢戏
    val statusBarTint = if (MiuixTheme.colorScheme.onBackground.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }
    // 状态栏高度：顶部那条模糊层按它定高
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 顶部搜索：false = 只显示标题，true = 输入框向左展开
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // 展开进度：同时驱动标题框收缩、输入框展开的宽度与透明度
    val searchExpand by animateFloatAsState(
        targetValue = if (searching) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "searchExpand"
    )
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // 展开后自动聚焦并弹键盘；收起时释放焦点、清空输入
    LaunchedEffect(searching) {
        if (searching) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
            query = ""
        }
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
                // 预览里不加载这张 2MB 的背景图，避免 layoutlib 解码超时导致渲染失败
                if (!inPreview) {
                    Image(
                        painter = painterResource(R.drawable.bg_main),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // 页面铺满整屏：不再为顶栏预留高度，内容可以一直延伸到状态栏下方
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
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

            // 状态栏那一条：只盖住状态栏 + 一小段余量，底边用 DstOut 渐隐，
            // 让模糊平滑过渡到下面的清晰页面，不会出现硬边
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight + 12.dp)
                    // 离屏层：让 DstOut 只作用在这一条区域内
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .then(
                        if (inPreview) {
                            // 预览退化成一层纯色
                            Modifier.background(color = statusBarTint)
                        } else {
                            Modifier.drawBackdrop(
                                backdrop = imageBackdrop,
                                shape = { RectangleShape },
                                effects = {
                                    // 微微一层：只做磨砂 + 提鲜
                                    vibrancy()
                                    blur(10.dp.toPx())
                                },
                                // 不要高光与阴影，否则矩形边缘会露出亮线/暗边
                                highlight = { null },
                                shadow = { null },
                                onDrawSurface = { drawRect(statusBarTint) }
                            )
                        }
                    )
                    .drawWithContent {
                        drawContent()
                        // 末尾 18dp 逐渐擦成透明，形成柔和的分界
                        val fade = 18.dp.toPx()
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
            )

            // 顶部悬浮层：左侧标题胶囊、右侧搜索按钮。
            // 点搜索后长条从按钮左侧往左展开，标题胶囊同步收缩成一个圆球
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .padding(horizontal = 20.dp)
            ) {
                // 与底部 FAB 同尺寸，圆心才会和它严格对齐
                val actionSize = 64.dp
                val gap = 12.dp
                // 标题框与底栏等宽：整行扣掉右侧按钮和中间那道间距
                val barWidth = maxWidth - actionSize - gap

                // 搜索输入框：右边缘贴着按钮，宽度从 0 长到最大，
                // 看起来就是从右侧按钮那边往左推开的；左侧要给收缩后的标题球留位置
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        // 给右侧按钮让出位置，宽度从这里往左长
                        .padding(end = actionSize + gap)
                        .width((barWidth - actionSize - gap) * searchExpand)
                        .height(actionSize)
                        .graphicsLayer { alpha = searchExpand }
                        .then(
                            if (inPreview) {
                                Modifier.background(color = topBarTint, shape = Capsule())
                            } else {
                                Modifier.drawBackdrop(
                                    backdrop = imageBackdrop,
                                    shape = { Capsule() },
                                    effects = {
                                        vibrancy()
                                        blur(5.dp.toPx())
                                        lens(20.dp.toPx(), 36.dp.toPx())
                                    },
                                    highlight = { Highlight.Default },
                                    shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.2f)) },
                                    onDrawSurface = { drawRect(topBarTint) }
                                )
                            }
                        )
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searching) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MiuixTheme.textStyles.body2.copy(
                                color = MiuixTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            // 键盘上的"搜索"键：先只收起面板，真正的搜索逻辑待接入
                            keyboardActions = KeyboardActions(onSearch = { searching = false }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = "搜索",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                // 标题框：宽度与底栏相同、左边缘也对齐，看起来就像上下同一条；
                // 展开搜索时向右收窄，最后只剩一个和搜索按钮同大的圆球
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(barWidth - (barWidth - actionSize) * searchExpand)
                        .height(actionSize)
                        .graphicsLayer {
                            // 收缩过程中文字会超出球体，按胶囊裁剪，避免压到搜索条上
                            clip = true
                            shape = Capsule()
                        }
                        .then(
                            if (inPreview) {
                                Modifier.background(color = topBarTint, shape = Capsule())
                            } else {
                                Modifier.drawBackdrop(
                                    backdrop = imageBackdrop,
                                    shape = { Capsule() },
                                    effects = {
                                        // 和底栏同一套：提鲜 + 磨砂 + 边缘折射
                                        vibrancy()
                                        blur(5.dp.toPx())
                                        lens(20.dp.toPx(), 36.dp.toPx())
                                    },
                                    highlight = { Highlight.Default },
                                    shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.2f)) },
                                    onDrawSurface = { drawRect(topBarTint) }
                                )
                            }
                        ),
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
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }

                // 搜索按钮：与标题框、底栏同一材质，展开时图标变成关闭
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(actionSize)
                        .then(
                            if (inPreview) {
                                Modifier.background(color = topBarTint, shape = Capsule())
                            } else {
                                Modifier.drawBackdrop(
                                    backdrop = imageBackdrop,
                                    shape = { Capsule() },
                                    effects = {
                                        vibrancy()
                                        blur(5.dp.toPx())
                                        lens(20.dp.toPx(), 36.dp.toPx())
                                    },
                                    highlight = { Highlight.Default },
                                    shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.2f)) },
                                    onDrawSurface = { drawRect(topBarTint) }
                                )
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { searching = !searching },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        imageVector = if (searching) {
                            MiuixIcons.Regular.Close
                        } else {
                            MiuixIcons.Regular.Search
                        },
                        contentDescription = if (searching) "关闭搜索" else "搜索",
                        modifier = Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary)
                    )
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
