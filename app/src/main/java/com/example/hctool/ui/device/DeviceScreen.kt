package com.example.hctool.ui.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hctool.ui.components.frostedSurface
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设备页：顶部「设备地图」框（标题 + 编辑按钮 + 网格地图），
 * 框外下方是纯文字副标题栏「设备列表」，再往下是展示每台设备的瀑布流卡片。
 *
 * @param store     设备数据（提升到 pager 外层持有）
 * @param backdrop  玻璃采样源（用 imageBackdrop，只采背景图）
 * @param inPreview 预览态退化玻璃为纯色
 */
@Composable
fun DeviceScreen(
    store: DeviceStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    modifier: Modifier = Modifier,
    onDialogOpenChange: (Boolean) -> Unit = {},
) {
    // 第一次点空格子 → 该格进入「待添加」态显示加号；再点加号 → 弹添加框
    var pendingCell by remember { mutableStateOf<Int?>(null) }
    // 非空表示正在为该格子添加设备
    var addDialogCell by remember { mutableStateOf<Int?>(null) }
    // 地图编辑弹框开关
    var editOpen by remember { mutableStateOf(false) }

    // 任一子级弹窗打开时通知外层锁定主界面左右切页
    LaunchedEffect(editOpen, addDialogCell) {
        onDialogOpenChange(editOpen || addDialogCell != null)
    }

    val map = store.selectedMap
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 顶部让开悬浮标题胶囊，底部让开悬浮底栏
                .padding(top = statusTop + 90.dp)
                .padding(bottom = navBottom + 120.dp)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            DeviceMapCard(
                store = store,
                backdrop = backdrop,
                inPreview = inPreview,
                pendingCell = pendingCell,
                onEdit = { editOpen = true },
                onCellClick = { cell ->
                    when {
                        // 格子上已有设备：暂不处理（后续可接设备详情）
                        store.deviceAt(map.id, cell) != null -> Unit
                        // 已处于待添加态：第二次点击，打开添加框
                        pendingCell == cell -> addDialogCell = cell
                        // 空格子首次点击：标记为待添加，显示加号
                        else -> pendingCell = cell
                    }
                }
            )

            // 副标题栏：纯文字「设备列表」
            Text(
                text = "设备列表",
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )

            DeviceMasonry(
                devices = store.devicesOf(map.id),
                backdrop = backdrop,
                inPreview = inPreview
            )
        }

        if (editOpen) {
            MapEditDialog(
                backdrop = backdrop,
                inPreview = inPreview,
                store = store,
                onDismiss = { editOpen = false }
            )
        }

        addDialogCell?.let { cell ->
            AddDeviceDialog(
                backdrop = backdrop,
                inPreview = inPreview,
                onDismiss = {
                    addDialogCell = null
                    pendingCell = null
                },
                onConfirm = { nickname, number, model, imageUri ->
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
                    addDialogCell = null
                    pendingCell = null
                }
            )
        }
    }
}

/**
 * 「设备地图」框：左上角标题、右上角编辑按钮，内部其余空间是网格地图。
 */
@Composable
private fun DeviceMapCard(
    store: DeviceStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    pendingCell: Int?,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "设备地图",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                GlassIconButton(
                    backdrop = backdrop,
                    icon = MiuixIcons.Regular.Edit,
                    contentDescription = "编辑地图",
                    onClick = onEdit,
                    size = 40.dp,
                    frosted = true
                )
            }
            // 当前地图名 + 规格，方便区分多张地图
            Text(
                text = "${map.name} · ${map.rows}×${map.cols}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            MapPager(
                store = store,
                pendingCell = pendingCell,
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
    pendingCell: Int?,
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
            val id = maps[page.mod(size)].id
            if (id != store.selectedMapId) store.selectMap(id)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
        // 页间距：左右切换时相邻网格不贴在一起
        pageSpacing = 16.dp
    ) { page ->
        GridMap(
            map = maps[page.mod(size)],
            store = store,
            pendingCell = pendingCell,
            onCellClick = onCellClick
        )
    }
}

/**
 * 点阵网格地图：按地图行列铺满，每格正方形。
 */
@Composable
private fun GridMap(
    map: DeviceMap,
    store: DeviceStore,
    pendingCell: Int?,
    onCellClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(map.rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(map.cols) { col ->
                    val cell = row * map.cols + col
                    GridCell(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        device = store.deviceAt(map.id, cell),
                        armed = pendingCell == cell,
                        onClick = { onCellClick(cell) }
                    )
                }
            }
        }
    }
}

/**
 * 单个网格格子：
 * - 有设备 → 绿色圆角色块 + 设备名称
 * - 待添加（已点过一次）→ 显示加号
 * - 空 → 淡淡的底色
 */
@Composable
private fun GridCell(
    modifier: Modifier = Modifier,
    device: Device?,
    armed: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    device != null -> DeviceBlockColor
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
            device != null -> Text(
                text = device.nickname,
                style = MiuixTheme.textStyles.body2,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(2.dp)
            )
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
    backdrop: Backdrop,
    inPreview: Boolean,
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
            left.forEach { DeviceCard(it, backdrop, inPreview) }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            right.forEach { DeviceCard(it, backdrop, inPreview) }
        }
    }
}

/**
 * 单张设备卡片：
 * - 主标题 = 设备昵称，左侧一个状态圆点（空闲绿 / 使用中蓝）
 * - 副标题 = 从设备编号正则提取的序号
 * - 有图片则展示图片，无图片则不展示
 */
@Composable
private fun DeviceCard(
    device: Device,
    backdrop: Backdrop,
    inPreview: Boolean,
) {
    val bitmap = rememberImageBitmap(device.imageUri)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(22.dp),
                inPreview = inPreview
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (device.status == DeviceStatus.IN_USE) {
                            StatusInUseColor
                        } else {
                            StatusIdleColor
                        }
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = device.nickname,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        val seq = extractSequence(device.number)
        Text(
            text = seq?.let { "序号 $it" } ?: "未编号",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // 备注图片放在编号下方，高度随图片宽高比自适应（不同卡片高矮错落）
        if (bitmap != null) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            Image(
                bitmap = bitmap,
                contentDescription = device.nickname,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}
