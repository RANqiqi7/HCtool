package com.example.hctool.ui.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hctool.ui.components.frostedSurface
import com.example.hctool.ui.order.OrderStore
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设备页：顶部「设备地图」框（标题 + 编辑按钮 + 网格地图），
 * 框外下方是磨砂外框副标题栏「设备列表」（右侧筛选按钮），再往下是展示每台设备的瀑布流卡片。
 * 筛选列表挂在根 Box 上叠加弹出：副标题栏在屏幕上半部分时向下展开、反之向上，
 * 边框不超过悬浮顶栏与底栏；标签按组平铺（组间分割线），不挤压下方卡片。
 *
 * @param store      设备数据（提升到 pager 外层持有）
 * @param orderStore 订单数据（设备运行状态由关联订单节点推导，状态圆点 / 筛选都要用）
 * @param backdrop   玻璃采样源（用 imageBackdrop，只采背景图）
 * @param inPreview  预览态退化玻璃为纯色
 * @param addMode    右下角加号是否处于「添加设备」模式：为 true 时网格上所有空格子都显示加号
 */
@Composable
fun DeviceScreen(
    store: DeviceStore,
    orderStore: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    addMode: Boolean,
    modifier: Modifier = Modifier,
    onDialogOpenChange: (Boolean) -> Unit = {},
    onDeviceClick: (Device) -> Unit = {},
) {
    // 非空表示正在为该格子添加设备
    var addDialogCell by remember { mutableStateOf<Int?>(null) }
    // 地图编辑弹框开关
    var editOpen by remember { mutableStateOf(false) }
    // 筛选：null = 不筛选（显示全部地图的全部设备）
    var activeFilter by remember { mutableStateOf<FilterTag?>(null) }
    // 筛选下拉列表开关
    var filterMenuOpen by remember { mutableStateOf(false) }
    // 记录副标题栏顶 / 底边在整页中的 y 坐标，用于判断筛选列表展开方向与钉位
    var headerTop by remember { mutableStateOf(0.dp) }
    var headerBottom by remember { mutableStateOf(0.dp) }
    // 记录整页高度，用于判断上 / 下半部分与列表可用最大高度
    var rootH by remember { mutableStateOf(0.dp) }
    // 筛选列表的数据源：真实设备名称与真实地图名称，有多少显示多少
    val deviceNames = store.devices.map { it.nickname }.distinct()
    val mapNames = store.maps.map { it.name }.distinct()
    // 筛选列表三组：状态 / 地图名称 / 设备名称（地图名在设备名之前），组间用分割线隔开
    val filterGroups = listOf(
        listOf(FilterTag(0, "空闲"), FilterTag(0, "使用中")),
        mapNames.map { FilterTag(2, it) },
        deviceNames.map { FilterTag(1, it) },
    )
    // 共享筛选面板用「组下标 + 项下标」标记选中项
    val activeIndex = activeFilter?.let { af ->
        val gi = filterGroups.indexOfFirst { af in it }
        if (gi < 0) null else gi to filterGroups[gi].indexOf(af)
    }

    // 任一子级弹窗打开时通知外层锁定主界面左右切页
    LaunchedEffect(editOpen, addDialogCell) {
        onDialogOpenChange(editOpen || addDialogCell != null)
    }

    val map = store.selectedMap
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current

    // 未筛选 = 全部地图的全部设备；筛选后 = 对应筛选结果
    val allDevices = store.devices
    val shownDevices: List<Device> = when (val f = activeFilter) {
        null -> allDevices.toList()
        else -> allDevices.filter { matchFilter(f, it, store, orderStore) }
    }

    // 展开方向：副标题栏中心在屏幕上半部分 → 向下展开，反之向上
    val headerCenter = (headerTop + headerBottom) / 2
    val expandDown = headerCenter <= rootH / 2
    // 筛选列表边框的上下界限：不越过悬浮顶栏与底栏（与 Column 让开浮层的内边距同基准）
    val topLimit = statusTop + 90.dp
    val bottomLimit = rootH - navBottom - 120.dp
    // 向下 / 向上展开时各自的可用最大高度，保证列表边框落在上下底栏之间
    val panelMaxH = (if (expandDown) bottomLimit - headerBottom - 6.dp else headerTop - 6.dp - topLimit)
        .coerceAtLeast(120.dp)

    // 设备内容采样层：单独记录「设备地图框 + 设备列表」这一层，供上层玻璃采样，
    // 让它们展开时能把这些组件一起磨进背景（而不只是背景图）。
    // 筛选列表与弹窗都是本层的兄弟节点（排在其后绘制），不会自采样导致崩溃
    val deviceContentBackdrop = rememberLayerBackdrop { drawContent() }
    // 上层玻璃（筛选列表 / 弹窗）的统一采样源：背景图 + 设备内容（地图框 + 列表）
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, deviceContentBackdrop)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootH = with(density) { it.height.toDp() } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 记录设备地图框 + 设备列表这一层，供筛选列表玻璃采样
                .layerBackdrop(deviceContentBackdrop)
                .verticalScroll(rememberScrollState())
                // 顶部让开悬浮标题胶囊，底部让开悬浮底栏
                .padding(top = statusTop + 90.dp)
                .padding(bottom = navBottom + 120.dp)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            DeviceMapCard(
                store = store,
                orderStore = orderStore,
                backdrop = backdrop,
                inPreview = inPreview,
                addMode = addMode,
                activeFilter = activeFilter,
                onEdit = { editOpen = true },
                onCellClick = { cell ->
                    // 添加模式下点空格子 → 弹添加框；格子上已有设备则不响应
                    if (addMode && store.deviceAt(map.id, cell) == null) {
                        addDialogCell = cell
                    }
                }
            )

            // 副标题栏：磨砂圆角外框「设备列表」居左，框内右侧筛选按钮；
            // 筛选列表挂在根 Box 上叠加显示，不参与这里的高度，不会把卡片挤下去
            Box(modifier = Modifier.fillMaxWidth()) {
                ListHeader(
                    backdrop = backdrop,
                    inPreview = inPreview,
                    title = "设备列表",
                    filterActive = activeFilter != null,
                    onToggleFilter = { filterMenuOpen = !filterMenuOpen },
                    onPositionChanged = { top, bottom ->
                        headerTop = top
                        headerBottom = bottom
                    }
                )
            }

            DeviceMasonry(
                devices = shownDevices,
                orderStore = orderStore,
                backdrop = backdrop,
                inPreview = inPreview,
                onDeviceClick = onDeviceClick
            )
        }

        // 筛选列表展开时铺一层透明遮罩：点列表外任意处平滑收起。
        // 排在 Column 之后，盖住整页内容但排在筛选列表之下
        if (filterMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { filterMenuOpen = false }
            )
        }

        // 筛选列表：挂在根 Box 上叠加显示（根 Box 已 fillMaxSize，不会挤压下方卡片）。
        // 副标题栏在屏幕上半部分时钉在其正下方往下展开，反之钉在其正上方往上展开；
        // 高度被钳制在上下底栏之间。出现 / 关闭都带平滑效果，展开方向与钉位同向。
        // 用全限定名调顶层 AnimatedVisibility：本页外层是 Column，
        // 导入后会优先匹配到 ColumnScope 的重载而报隐式接收者错误
        androidx.compose.animation.AnimatedVisibility(
            visible = filterMenuOpen,
            // 纯高度拉伸：内容高度从 0 渐变到满，带动面板上下边框平滑拉长 / 收回，不闪现
            enter = expandVertically(
                expandFrom = if (expandDown) Alignment.Top else Alignment.Bottom,
                animationSpec = tween(220)
            ),
            exit = shrinkVertically(
                shrinkTowards = if (expandDown) Alignment.Top else Alignment.Bottom,
                animationSpec = tween(200)
            ),
            modifier = if (expandDown) {
                Modifier
                    .align(Alignment.TopEnd)
                    // 与 Column 的 20dp 水平内边距对齐，右边线跟筛选按钮齐平
                    .offset(x = -20.dp, y = headerBottom + 6.dp)
            } else {
                Modifier
                    .align(Alignment.BottomEnd)
                    // 向上展开：底边钉在副标题栏顶边上方 6dp
                    .offset(x = -20.dp, y = headerTop - 6.dp - rootH)
            }
        ) {
            FilterMenu(
                backdrop = combinedBackdrop,
                inPreview = inPreview,
                groups = filterGroups.map { g -> g.map { it.label } },
                active = activeIndex,
                maxHeight = panelMaxH,
                onPick = { gi, ii ->
                    val tag = filterGroups[gi][ii]
                    // 再点已选中标签 = 取消筛选
                    activeFilter = if (activeFilter == tag) null else tag
                    filterMenuOpen = false
                }
            )
        }

        if (editOpen) {
            MapEditDialog(
                backdrop = combinedBackdrop,
                inPreview = inPreview,
                store = store,
                onDismiss = { editOpen = false }
            )
        }

        addDialogCell?.let { cell ->
            AddDeviceDialog(
                backdrop = combinedBackdrop,
                inPreview = inPreview,
                // 只关弹窗；addMode 保持，其余空格子继续显示加号，可接着添加下一台
                onDismiss = { addDialogCell = null },
                onConfirm = { nickname, number, model, imageUri ->
                    // 只写数据；关闭由弹窗自己 requestClose 滑出后经 onDismiss 完成
                    store.addDevice(
                        Device(
                            id = DeviceStore.newId(),
                            nickname = nickname,
                            number = number,
                            model = model,
                            imageUri = imageUri,
                            mapId = map.id,
                            cell = cell
                        )
                    )
                }
            )
        }
    }
}

/**
 * 「设备地图」框：头部标题居左、右编辑按钮(无圆框)；下方常驻地图名与网格地图。
 */
@Composable
private fun DeviceMapCard(
    store: DeviceStore,
    orderStore: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    addMode: Boolean,
    activeFilter: FilterTag?,
    onEdit: () -> Unit,
    onCellClick: (Int) -> Unit,
) {
    val map = store.selectedMap
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frostedSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(28.dp),
                inPreview = inPreview
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 头部：标题居左、右编辑按钮(纯图标无圆框)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Text(
                    text = "设备地图",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onEdit
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        imageVector = MiuixIcons.Regular.Edit,
                        contentDescription = "编辑地图",
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    )
                }
            }
            // 当前地图名 + 规格，方便区分多张地图
            Text(
                text = "${map.name} · ${map.rows}×${map.cols}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            MapPager(
                store = store,
                orderStore = orderStore,
                addMode = addMode,
                activeFilter = activeFilter,
                onCellClick = onCellClick
            )
        }
    }
}

/**
 * 地图平铺切换器：与主页面一样的左右滑动平铺翻页，且循环滚动（末张再滑回第一张）。
 * 用超大页数 + 取余映射到真实地图下标实现循环；
 * 用户滑动回写 store.selectedMapId，外部切换/增删地图时再无动画对齐回对应页。
 */
@Composable
private fun MapPager(
    store: DeviceStore,
    orderStore: OrderStore,
    addMode: Boolean,
    activeFilter: FilterTag?,
    onCellClick: (Int) -> Unit,
) {
    val maps = store.maps
    val size = maps.size
    val selectedIndex = maps.indexOfFirst { it.id == store.selectedMapId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = Int.MAX_VALUE / 2,
        pageCount = { Int.MAX_VALUE }
    )

    // 外部切换选中地图 / 地图增删导致取余映射变化时，无动画对齐到对应同余页
    LaunchedEffect(store.selectedMapId, size) {
        val base = pagerState.currentPage
        val delta = (selectedIndex - base.mod(size) + size) % size
        if (delta != 0) pagerState.scrollToPage(base + delta)
    }
    // 用户滑动翻页 → 回写 store，驱动标题/设备列表同步
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            // 现取地图数：本 effect 只启动一次，不能用外部捕获的旧 size，
            // 否则增删地图后取余永远按旧数量算，滑动时地图名不会变
            val count = maps.size
            val id = maps[page.mod(count)].id
            if (id != store.selectedMapId) store.selectMap(id)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = 6.dp
        // 当前宽度下某张地图网格的自然高度：方格按行铺满
        fun heightOf(m: DeviceMap): Dp {
            val cell = (maxWidth - spacing * (m.cols - 1f)) / m.cols.toFloat()
            return cell * m.rows.toFloat() + spacing * (m.rows - 1f)
        }
        // 不同规格地图切换后平滑收缩/拉长，避免外框突然跳变
        val gridHeight by animateDpAsState(
            targetValue = heightOf(store.selectedMap),
            animationSpec = tween(durationMillis = 240),
            label = "gridHeight"
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
            // 页间距：左右切换时相邻网格不贴在一起
            pageSpacing = 16.dp
        ) { page ->
            GridMap(
                map = maps[page.mod(size)],
                store = store,
                orderStore = orderStore,
                addMode = addMode,
                activeFilter = activeFilter,
                onCellClick = onCellClick,
                height = gridHeight
            )
        }
    }
}

/**
 * 点阵网格地图：按地图行列铺满；行高由容器高度反算，
 * 收缩/拉长动画期间格子随之伸缩，停稳后恢复正方形。
 */
@Composable
private fun GridMap(
    map: DeviceMap,
    store: DeviceStore,
    orderStore: OrderStore,
    addMode: Boolean,
    activeFilter: FilterTag?,
    onCellClick: (Int) -> Unit,
    height: Dp,
) {
    val spacing = 6.dp
    // 行高由容器高度反算：动画期间格子被拉伸，停稳后正好是方格
    val cellHeight = (height - spacing * (map.rows - 1f)) / map.rows.toFloat()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        repeat(map.rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                repeat(map.cols) { col ->
                    val cell = row * map.cols + col
                    val device = store.deviceAt(map.id, cell)
                    GridCell(
                        modifier = Modifier
                            .weight(1f)
                            .height(cellHeight),
                        device = device,
                        // 添加模式下所有空格子都亮加号
                        armed = addMode && device == null,
                        // 选中筛选标签时：不匹配的设备色块降透明度，突出筛选结果
                        dimmed = device != null &&
                            activeFilter != null &&
                            !matchFilter(activeFilter, device, store, orderStore),
                        onClick = { onCellClick(cell) }
                    )
                }
            }
        }
    }
}

/**
 * 单个网格格子：
 * - 有设备 → 绿色圆角色块，内部双行：第一行设备名称、第二行设备序号
 * - 待添加（已点过一次）→ 显示加号
 * - 空 → 淡淡的底色
 * - dimmed（筛选不匹配）→ 色块与文字降透明度，突出筛选出的设备
 */
@Composable
private fun GridCell(
    modifier: Modifier = Modifier,
    device: Device?,
    armed: Boolean,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    device != null -> if (dimmed) {
                        DeviceBlockColor.copy(alpha = 0.3f)
                    } else {
                        DeviceBlockColor
                    }
                    armed -> MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
                    // 空格子底色稍明显一点，保证点阵网格清晰可见
                    else -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            device != null -> Column(
                modifier = Modifier.padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = device.nickname,
                    style = MiuixTheme.textStyles.body2,
                    color = Color.White.copy(alpha = if (dimmed) 0.5f else 1f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 第二行序号：与设备列表卡片同一套正则提取规则
                Text(
                    text = extractSequence(device.number)?.let { "序号 $it" } ?: "未编号",
                    style = MiuixTheme.textStyles.body2,
                    color = Color.White.copy(alpha = if (dimmed) 0.4f else 0.75f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            armed -> Image(
                imageVector = MiuixIcons.Regular.Add,
                contentDescription = "添加设备",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary)
            )
        }
    }
}

/**
 * 设备列表：两列瀑布流（左右交替分配，卡片高度因有无图片而错落）。
 */
@Composable
private fun DeviceMasonry(
    devices: List<Device>,
    orderStore: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    onDeviceClick: (Device) -> Unit,
) {
    if (devices.isEmpty()) {
        Text(
            text = "暂无设备，点击网格中的格子添加设备",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        return
    }
    val left = devices.filterIndexed { index, _ -> index % 2 == 0 }
    val right = devices.filterIndexed { index, _ -> index % 2 == 1 }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            left.forEach { DeviceCard(it, orderStore, backdrop, inPreview, onDeviceClick) }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            right.forEach { DeviceCard(it, orderStore, backdrop, inPreview, onDeviceClick) }
        }
    }
}

/** 筛选标签：group 区分类型（0 状态 / 1 设备名称 / 2 地图名称），label 为显示与匹配文本 */
private data class FilterTag(val group: Int, val label: String)

/** 按筛选标签过滤单台设备 */
private fun matchFilter(
    f: FilterTag,
    d: Device,
    store: DeviceStore,
    orderStore: OrderStore,
): Boolean = when (f.group) {
    0 -> d.computeStatus(orderStore) == (if (f.label == "空闲") DeviceStatus.IDLE else DeviceStatus.IN_USE)
    1 -> d.nickname == f.label
    else -> store.maps.firstOrNull { it.id == d.mapId }?.name == f.label
}

// 「设备列表」副标题栏与筛选面板已上移到 DeviceWidgets 的 ListHeader / FilterMenu，与订单页共用。

