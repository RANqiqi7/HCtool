package com.example.hctool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hctool.R
import com.example.hctool.ui.components.FloatingBottomBar
import com.example.hctool.ui.components.FloatingBottomBarItem
import com.example.hctool.ui.components.GlassButton
import com.example.hctool.ui.components.GlassFab
import com.example.hctool.ui.components.blockClicks
import com.example.hctool.ui.components.glassSurface
import com.example.hctool.ui.components.AppBackgroundImage
import com.example.hctool.ui.data.LocalDataStore
import com.example.hctool.ui.data.toDeviceSeed
import com.example.hctool.ui.device.Device
import com.example.hctool.ui.device.DeviceDetailScreen
import com.example.hctool.ui.device.DeviceScreen
import com.example.hctool.ui.device.DeviceStore
import com.example.hctool.ui.device.defaultDeviceSeed
import com.example.hctool.ui.device.hideIme
import com.example.hctool.ui.device.showIme
import com.example.hctool.ui.order.AddOrderDialog
import com.example.hctool.ui.order.Order
import com.example.hctool.ui.order.OrderDetailScreen
import com.example.hctool.ui.order.OrderScreen
import com.example.hctool.ui.order.OrderStore
import com.example.hctool.ui.order.currentSubmitTime
import com.example.hctool.ui.order.defaultPresetOrders
import com.example.hctool.ui.settings.SettingsScreen
import com.example.hctool.ui.sync.SyncManager
import com.example.hctool.ui.sync.rememberSyncManager
import com.example.hctool.ui.theme.HCtoolTheme
import com.example.hctool.ui.theme.LocalAppSettings
import com.example.hctool.ui.theme.rememberAppSettings
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 全局设置（主题模式 / 自定义背景）在此创建一份，向下提供；
            // HCtoolTheme 读它决定深浅色，背景绘制与设置页也读同一份
            val settings = rememberAppSettings()
            CompositionLocalProvider(LocalAppSettings provides settings) {
                HCtoolTheme {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalCoroutinesApi::class)
@Composable
fun MainScreen() {
    // 当前选中的标签下标，赋值后会触发重组刷新界面
    var selectedIndex by remember { mutableStateOf(0) }
    // 标签文字与图标保持不变（两个列表按下标一一对应）
    val items = listOf("设备", "订单")
    val icons = listOf(
        MiuixIcons.Regular.SearchDevice,
        MiuixIcons.Regular.ListView
    )

    // 两个页面平铺，左右滑动切换
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    // 数据商店：优先从磁盘快照恢复（首次安装无文件时用预置演示数据作种子）；
    // 提升到 pager 外层持有，翻页销毁重建也不丢数据
    val context = LocalContext.current
    val initialSnapshot = remember { LocalDataStore.load(context) }
    // 设备页数据：地图 + 设备 + 选中地图，整组从种子恢复
    val deviceStore = remember { DeviceStore(initialSnapshot?.toDeviceSeed() ?: defaultDeviceSeed()) }
    // 设备页是否有子级弹窗（新增设备 / 地图编辑）打开：打开时禁止主界面左右滑动切页
    var deviceDialogOpen by remember { mutableStateOf(false) }
    // 当前打开详情的设备：非空时在设备页之上滑入整屏详情页浮层
    var detailDevice by remember { mutableStateOf<Device?>(null) }
    // 订单页数据：同样从快照恢复，无文件时用预置订单
    val orderStore = remember {
        OrderStore(initialSnapshot?.orders?.ifEmpty { null } ?: defaultPresetOrders())
    }
    // 新增订单弹窗开关（订单页点底栏加号弹出）
    var addOrderOpen by remember { mutableStateOf(false) }
    // 当前打开详情的订单：非空时在订单页之上滑入整屏详情页浮层
    var detailOrder by remember { mutableStateOf<Order?>(null) }
    // 设备页「添加设备」模式：右下角加号按下后变减号，网格上所有空格子亮加号；
    // 每完成一台添加其他加号保留，可连续添加；再按减号才关闭全部加号
    var deviceAddMode by remember { mutableStateOf(false) }
    // 设置页开关：设备页顶栏齿轮按钮打开，整屏浮层盖住主页面
    var settingsOpen by remember { mutableStateOf(false) }

    // 云同步调度：配置在设置页「云同步」卡片里填，未开启时内部静默跳过
    val syncManager = rememberSyncManager(LocalAppSettings.current, deviceStore, orderStore)

    // 离设备页（翻页 / 进详情）时自动退出添加模式，避免减号留在其它页上
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != 0) deviceAddMode = false
    }

    // 翻页 → 同步底栏高亮与顶栏标题
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { selectedIndex = it }
    }

    // 数据变更 → 防抖 1.5s 落盘：杀进程重启不丢数据；
    // 编码出的 JSON 同时是云同步报文（SyncManager 复用同一份编码）
    LaunchedEffect(deviceStore, orderStore) {
        snapshotFlow {
            LocalDataStore.encode(LocalDataStore.snapshotOf(deviceStore, orderStore))
        }
            .distinctUntilChanged()
            .debounce(1500)
            .collect { text -> LocalDataStore.saveJson(context, text) }
    }

    // 启动后自动拉一次（稍等落地，避开首帧重组高峰）
    LaunchedEffect(Unit) {
        delay(800)
        syncManager.syncNow()
    }
    // 数据变更 → 防抖 2s 自动同步（拉平 + 推平，冲突时弹窗让用户裁决）
    LaunchedEffect(syncManager) {
        snapshotFlow {
            LocalDataStore.encode(LocalDataStore.snapshotOf(deviceStore, orderStore))
        }
            .distinctUntilChanged()
            .debounce(2000)
            .collect { syncManager.syncNow() }
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
    // 顶栏玻璃的采样源：记录「背景图 + 页面内容（设备地图/列表）」，
    // 但不含顶栏自身，避免顶栏在层内采样自己所在的渲染目标导致崩溃
    val contentBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    // 底栏（FloatingBottomBar）用的是 miuix-blur 那一套液态玻璃，
    // 需要一个 miuix 的采样源，记录同一份页面内容供其折射/色散取用
    val glassBackdrop = rememberMiuixLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    // Compose Preview（layoutlib）不支持 RenderEffect，也不适合解码大图：
    // 预览里把玻璃退化成纯色块并跳过背景图，保证实时渲染能出图，真机不受影响
    val inPreview = LocalInspectionMode.current

    // 顶部搜索：false = 只显示标题，true = 输入框向左展开（仅订单页有此按钮）
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // 搜索只属于订单页：离开订单页（翻页 / 进详情）时自动收起搜索面板，
    // searchWasOpen 守卫会同步清焦点与收键盘
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != 1) searching = false
    }
    // 展开进度：同时驱动标题框收缩、输入框展开的宽度与透明度
    val searchExpand by animateFloatAsState(
        targetValue = if (searching) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "searchExpand"
    )
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val rootView = LocalView.current

    // 展开后自动聚焦并弹键盘；仅当搜索真正打开过、收起时才释放焦点/收键盘，
    // 避免初始或无关重组误调 clearFocus/hide 打断弹窗内输入框的输入法；
    // show/hide 都走带可见性守卫的 showIme/hideIme（与 DeviceField 同一套），
    // 杜绝在键盘已显示 / 未显示时重发请求——竞争会把输入法 insets 动画卡死
    var searchWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(searching) {
        if (searching) {
            searchWasOpen = true
            focusRequester.requestFocus()
            // 等输入连接建立后再弹，与 DeviceField 同基准
            delay(60)
            showIme(rootView, keyboard)
        } else if (searchWasOpen) {
            searchWasOpen = false
            focusManager.clearFocus()
            hideIme(rootView, keyboard)
            query = ""
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 内容层：作为底栏的模糊来源（layerBackdrop 必须放在内容根节点上）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .miuixLayerBackdrop(glassBackdrop)
        ) {
            // 顶栏采样层：只记录背景图 + 页面内容，顶栏在它外面采样，避免自采崩溃
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop)
            ) {
                // 背景图单独再记一层，作为设备卡片的采样源
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(imageBackdrop)
                ) {
                    // 预览里不加载这张 2MB 的背景图，避免 layoutlib 解码超时导致渲染失败
                    AppBackgroundImage(
                        modifier = Modifier.fillMaxSize(),
                        inPreview = inPreview
                    )
                }

                // 页面铺满整屏：不再为顶栏预留高度，内容可以一直延伸到状态栏下方
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // 子级弹窗 / 设备详情页 / 订单详情页 / 新增订单弹窗 / 设置页打开时锁定左右切换
                    userScrollEnabled = !deviceDialogOpen && detailDevice == null &&
                        detailOrder == null && !addOrderOpen && !settingsOpen
                ) {
                    page ->
                    if (page == 0) {
                        // 设备页：设备地图框 + 设备列表瀑布流
                        DeviceScreen(
                            store = deviceStore,
                            orderStore = orderStore,
                            backdrop = imageBackdrop,
                            inPreview = inPreview,
                            addMode = deviceAddMode,
                            onDialogOpenChange = { deviceDialogOpen = it },
                            onDeviceClick = { detailDevice = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // 订单页：订单列表瀑布流（进行中优先），顶栏搜索框的关键词实时筛选
                        OrderScreen(
                            store = orderStore,
                            backdrop = imageBackdrop,
                            inPreview = inPreview,
                            query = query,
                            onOrderClick = { detailOrder = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 顶部悬浮层：设备页 = 标题 + 设置按钮；订单页 = 标题 + 搜索按钮，
            // 点搜索后长条从按钮左侧往左展开，标题胶囊同步收缩成一个圆球。
            // 设备页弹窗（新增/地图编辑）的遮罩在页面层内、画在顶栏之下，
            // 弹窗打开时顶栏仍浮在最上层，用 blockClicks 冻结其点击
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .blockClicks(deviceDialogOpen)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .padding(horizontal = 20.dp)
            ) {
                // 与底部 FAB 同尺寸，圆心才会和它严格对齐
                val actionSize = 64.dp
                val gap = 12.dp
                // 标题框与底栏等宽：整行扣掉右侧按钮和中间那道间距
                val barWidth = maxWidth - actionSize - gap

                // 面板会向下长高，所以不用 Capsule（高度一变就成椭圆），
                // 固定 32dp 圆角：收起时正好是胶囊，展开时是圆角矩形
                val searchShape = RoundedCornerShape(32.dp)

                // 搜索输入框：右边缘贴着按钮，宽度从 0 长到最大，
                // 看起来就是从右侧按钮那边往左推开的；左侧要给收缩后的标题球留位置。
                // 点右侧那个无边框箭头后，这块玻璃会向下延伸出快捷筛选面板
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        // 给右侧按钮让出位置，宽度从这里往左长
                        .padding(end = actionSize + gap)
                        .width((barWidth - actionSize - gap) * searchExpand)
                        // 高度由内容决定：收起时只有一行输入，展开后再加上标签面板
                        .heightIn(min = actionSize)
                        .graphicsLayer { alpha = searchExpand }
                        // 材质统一走 glassSurface（与右下角添加键同一套模板）
                        .glassSurface(
                            backdrop = contentBackdrop,
                            shape = searchShape,
                            inPreview = inPreview
                        )
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (searching) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(actionSize),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = query,
                                    onValueChange = {
                                        query = it
                                    },
                                    singleLine = true,
                                    textStyle = MiuixTheme.textStyles.body2.copy(
                                        color = MiuixTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    // 输入即实时筛选订单列表，键盘上的“搜索”键无需额外动作
                                    modifier = Modifier
                                        .weight(1f)
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
                    }
                }

                // 标题框：可点击，点一下收起搜索回到标题视图。
                // 材质与按压反馈统一走 GlassButton（即右下角添加键那套模板）。
                // 宽度与底栏相同、左边缘对齐；展开搜索时向右收窄成一个圆球。
                GlassButton(
                    backdrop = contentBackdrop,
                    onClick = {
                        // 点标题即收起搜索，回到普通顶栏
                        searching = false
                    },
                    // 固定 32dp 圆角：64dp 高下正好是胶囊，与搜索条材质一致
                    shape = searchShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(barWidth - (barWidth - actionSize) * searchExpand)
                        .heightIn(min = actionSize)
                        .graphicsLayer {
                            // 收缩过程中文字会超出球体，按圆角裁剪，避免压到搜索条上
                            clip = true
                            shape = searchShape
                        }
                ) {
                    // 标题行：固定 actionSize 高，跟随底栏选中项切换文字/图标。
                    // 展开态显示文字；向右收缩成球时文字淡出、换成该页图标
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(actionSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(
                            targetState = selectedIndex,
                            animationSpec = tween(durationMillis = 220),
                            label = "topBarTitle"
                        ) { index ->
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = items[index],
                                    style = MiuixTheme.textStyles.title3,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.graphicsLayer {
                                        // 收缩到一半时标题就淡出，把位置让给图标
                                        alpha = (1f - searchExpand * 2f).coerceIn(0f, 1f)
                                    }
                                )
                                Image(
                                    imageVector = icons[index],
                                    contentDescription = items[index],
                                    modifier = Modifier
                                        .size(28.dp)
                                        .graphicsLayer {
                                            // 收缩过一半才亮出图标，两者不会同时出现
                                            alpha = ((searchExpand - 0.5f) * 2f).coerceIn(0f, 1f)
                                        },
                                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                                )
                            }
                        }
                    }
                }

                // 顶栏右侧按钮：统一玻璃材质 + 按压反馈（GlassButton）。
                // 设备页 = 设置入口（打开设置页）；
                // 订单页 = 搜索开关，展开中变对勾关闭图标
                GlassButton(
                    backdrop = contentBackdrop,
                    onClick = {
                        when (selectedIndex) {
                            0 -> settingsOpen = true
                            1 -> searching = !searching
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(actionSize)
                ) {
                    Image(
                        imageVector = when {
                            selectedIndex == 0 -> MiuixIcons.Regular.Settings
                            searching -> MiuixIcons.Regular.Close
                            else -> MiuixIcons.Regular.Search
                        },
                        contentDescription = when {
                            selectedIndex == 0 -> "设置"
                            searching -> "关闭搜索"
                            else -> "搜索"
                        },
                        modifier = Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary)
                    )
                }
            }
        }

        // 底部悬浮层：左边是收缩后的玻璃底栏，右边是独立的玻璃加号按钮；
        // 与顶栏同理，设备页弹窗打开时冻结点击（遮罩在它之下盖不住它）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .blockClicks(deviceDialogOpen)
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
                FloatingBottomBar(
                    // 铺满整行（把右侧空间让给 FAB），宽度沿用你现在的布局
                    modifier = Modifier.weight(1f),
                    // 用「目标页」驱动滑块：animateScrollToPage 一调用就立即更新，滑块当场移动，
                    // 不再等翻页动画停稳（settledPage 会滞后整段滚动时间，造成延迟）
                    selectedIndex = { pagerState.targetPage },
                    onSelected = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    backdrop = glassBackdrop,
                    tabsCount = items.size,
                    // 预览（layoutlib）不支持 RenderEffect，退化为纯色
                    isBlurEnabled = !inPreview,
                ) {
                    items.forEachIndexed { index, label ->
                        FloatingBottomBarItem(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                        ) {
                            // 底栏只保留图标，去掉图标下的文字标题
                            Icon(
                                imageVector = icons[index],
                                contentDescription = label,
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                // 添加按钮：与底栏同高同材质，视觉上属于同一条悬浮层；
                // 设备页上它是「添加模式」开关（开启时变减号），订单页上直接弹新增订单
                GlassFab(
                    backdrop = backdrop,
                    icon = MiuixIcons.Regular.Add,
                    contentDescription = if (selectedIndex == 0 && deviceAddMode) "关闭添加设备" else "添加",
                    minus = selectedIndex == 0 && deviceAddMode
                ) {
                    when (selectedIndex) {
                        // 设备页：切换添加模式，开启后网格空格子全亮加号，再按关闭
                        0 -> deviceAddMode = !deviceAddMode
                        // 订单页：加号弹出新增订单
                        1 -> addOrderOpen = true
                    }
                }
            }
        }

        // 设备详情页浮层：点击设备卡片后整屏滑入，盖住顶栏 / 底栏；
        // 浮层在最外层 Box、位于 imageBackdrop 层之外采样，不会自采样导致崩溃
        detailDevice?.let { device ->
            DeviceDetailScreen(
                device = device,
                store = deviceStore,
                orderStore = orderStore,
                backdrop = imageBackdrop,
                inPreview = inPreview,
                onBack = { detailDevice = null },
                onEdit = { /* 设备编辑逻辑待接入 */ },
                // 跨模块跳转不叠浮层：关掉设备详情、把主翻页切到订单页，
                // 保证订单详情返回时落回订单管理页
                onOrderClick = { orderNo ->
                    detailDevice = null
                    detailOrder = orderStore.getOrAddByOrderNo(orderNo)
                    scope.launch { pagerState.scrollToPage(1) }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 订单详情页浮层：点击订单卡片后整屏滑入，盖住顶栏 / 底栏；
        // 同样位于 imageBackdrop 层之外采样，不会自采样导致崩溃
        detailOrder?.let { order ->
            OrderDetailScreen(
                order = order,
                store = orderStore,
                deviceStore = deviceStore,
                backdrop = imageBackdrop,
                inPreview = inPreview,
                onBack = { detailOrder = null },
                onEdit = { /* 订单编辑逻辑待接入 */ },
                // 跨模块跳转不叠浮层：关掉订单详情、把主翻页切到设备页，
                // 保证设备详情返回时落回设备管理页
                onDeviceClick = { device ->
                    detailOrder = null
                    detailDevice = device
                    scope.launch { pagerState.scrollToPage(0) }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 新增订单弹窗：订单页点底栏加号弹出；保存时记录提单时间（精确到年月日时分）
        if (addOrderOpen) {
            AddOrderDialog(
                backdrop = contentBackdrop,
                inPreview = inPreview,
                onDismiss = { addOrderOpen = false },
                onConfirm = { orderNo, customer, sampleName, sampleCount, sampleNo, testCondition, caseOwner, imageUri ->
                    orderStore.addOrder(
                        Order(
                            id = OrderStore.newId(),
                            orderNo = orderNo,
                            customer = customer,
                            sampleName = sampleName,
                            sampleCount = sampleCount,
                            sampleNo = sampleNo,
                            testCondition = testCondition,
                            caseOwner = caseOwner,
                            imageUri = imageUri,
                            submitTime = currentSubmitTime()
                        )
                    )
                    // 关闭由弹窗自己 requestClose 滑出后经 onDismiss 完成
                }
            )
        }

        // 设置页浮层：设备页顶栏齿轮打开；整屏自绘背景图遮住主页面，
        // 位于 imageBackdrop 层之外采样，不会自采样导致崩溃
        if (settingsOpen) {
            SettingsScreen(
                backdrop = imageBackdrop,
                inPreview = inPreview,
                syncManager = syncManager,
                onBack = { settingsOpen = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 数据冲突弹窗：本机与服务器都有改动时二选一（关掉前不允许其它同步）；
        // 放在最外层 Box 末尾，保证盖在设置页等所有浮层之上
        if (syncManager.conflictPending) {
            SyncConflictDialog(syncManager)
        }
    }
}

/** 同步冲突裁决弹窗：用服务器数据覆盖本机 / 把本机数据强推上云，选完即执行 */
@Composable
private fun SyncConflictDialog(syncManager: SyncManager) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { }, // 必须二选一，点外部不关
        title = { Text(text = "数据冲突", style = MiuixTheme.textStyles.title3) },
        text = {
            Text(
                text = "本机与服务器都有改动，请选择保留哪一边：",
                style = MiuixTheme.textStyles.body1
            )
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { syncManager.resolveConflict(useServer = true) } }) {
                Text(text = "用服务器数据覆盖本机", style = MiuixTheme.textStyles.body1)
            }
        },
        dismissButton = {
            TextButton(onClick = { scope.launch { syncManager.resolveConflict(useServer = false) } }) {
                Text(text = "把本机数据强推上云", style = MiuixTheme.textStyles.body1)
            }
        }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    // 预览也要 provide 一份 AppSettings，否则 HCtoolTheme / 背景读 LocalAppSettings 会报错
    val settings = rememberAppSettings()
    CompositionLocalProvider(LocalAppSettings provides settings) {
        HCtoolTheme {
            MainScreen()
        }
    }
}
