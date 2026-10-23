package com.example.hctool.ui.device

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hctool.R
import com.example.hctool.ui.components.frostedSurface
import com.example.hctool.ui.components.glassSurface
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设备详情页：点击设备列表里的卡片打开。
 *
 * 结构（自上而下）：
 * - 顶栏：左上返回按钮、右上编辑按钮，均为玻璃按钮（带按压轻微收缩）；
 * - 头部框：圆角磨砂玻璃，双行——第一行粗字设备名 + 居右状态圆点，第二行编号 / 序号；
 * - 四个大卡片上下排列：基础信息、关联订单、表单情况、更多信息，
 *   统一磨砂玻璃 + 点击轻微收缩 + 平滑伸缩（点击展开 / 收起）。
 *
 * 整页从右侧滑入，返回时先滑出再移除；详情页自绘一张同款背景图，
 * 既遮住下层设备页，又与玻璃采样源（imageBackdrop）对齐，保证磨砂折射一致。
 *
 * @param device    当前查看的设备
 * @param store     设备数据（用于取所属地图名与网格规格）
 * @param backdrop  玻璃采样源（用 imageBackdrop，只采背景图）
 * @param inPreview 预览态退化玻璃为纯色
 * @param onBack    返回（滑出动画结束后回调）
 * @param onEdit    编辑按钮点击（具体编辑逻辑待接入）
 */
@Composable
fun DeviceDetailScreen(
    device: Device,
    store: DeviceStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // 入场：挂载后翻转 shown，页面从右侧滑入；返回时先滑出、动画结束再真正移除
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    fun requestBack() {
        if (closing) return
        closing = true
        shown = false
        scope.launch {
            delay(300)
            onBack()
        }
    }
    // 系统返回键等同点返回按钮
    BackHandler { requestBack() }

    // 详情内容采样层：记录下方卡片，供悬浮顶栏玻璃采样（顶栏在该层之外，不会自采样）
    val detailContentBackdrop = rememberLayerBackdrop { drawContent() }
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, detailContentBackdrop)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenW = maxWidth
        val slide by animateDpAsState(
            targetValue = if (shown) 0.dp else screenW,
            animationSpec = tween(durationMillis = 300),
            label = "detailSlide"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 用布局 offset 驱动整屏滑入 / 滑出，保证内部磨砂卡片玻璃边框一起移动
                .offset(x = slide)
                // 吃掉落在空白处的点击，避免穿透到下层设备页
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            // 自绘背景图：遮住下层设备页；预览态退化为纯色
            if (!inPreview) {
                Image(
                    painter = painterResource(R.drawable.bg_main),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                )
            }

            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(detailContentBackdrop)
                    .verticalScroll(rememberScrollState())
                    // 首屏让开悬浮顶栏（状态栏 + 12 + 顶栏高 56 + 间距 16）；
                    // 滚动区铺满全屏，上滑时卡片可穿过顶栏下方，贯通无裁剪横线
                    .padding(top = statusTop + 12.dp + 56.dp + 16.dp)
                    .padding(bottom = navBottom + 24.dp)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BasicInfoCard(
                    device = device,
                    backdrop = backdrop,
                    inPreview = inPreview
                )
                AssociatedOrdersCard(
                    device = device,
                    backdrop = backdrop,
                    inPreview = inPreview
                )
                FormStatusCard(
                    device = device,
                    backdrop = backdrop,
                    inPreview = inPreview
                )
                MoreInfoCard(
                    device = device,
                    store = store,
                    backdrop = backdrop,
                    inPreview = inPreview
                )
            }
            // 悬浮顶栏浮在滚动内容之上：返回 + 标题胶囊(设备名+编号+状态圆点) + 编辑
            DetailTopBar(
                device = device,
                backdrop = combinedBackdrop,
                inPreview = inPreview,
                onBack = { requestBack() },
                onEdit = onEdit,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = statusTop + 12.dp)
                    .padding(horizontal = 20.dp)
            )
        }
    }
}

/**
 * 详情页悬浮顶栏：左返回、右编辑（玻璃图标按钮，自带按压缩放），
 * 中间是悬浮标题胶囊——与主页面顶部标题胶囊同款玻璃材质，内显设备名 + 居右状态圆点。
 * 整条顶栏固定不随内容滚动。
 */
@Composable
private fun DetailTopBar(
    device: Device,
    backdrop: Backdrop,
    inPreview: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(
            backdrop = backdrop,
            icon = MiuixIcons.Regular.Back,
            contentDescription = "返回",
            onClick = onBack,
            size = 56.dp
        )
        // 悬浮标题胶囊：设备名居左 + 状态圆点居右（glassSurface 通透玻璃，非磨砂卡片）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // 统一按压反馈
                .pressable(onClick = { })
                .glassSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(28.dp),
                    inPreview = inPreview
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.nickname,
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // 设备编号紧随名称展示
                Text(
                    text = device.number,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusDot(status = device.status, size = 10.dp)
            }
        }
        GlassIconButton(
            backdrop = backdrop,
            icon = MiuixIcons.Regular.Edit,
            contentDescription = "编辑",
            onClick = onEdit,
            size = 56.dp
        )
    }
}

/**
 * 通用信息卡片：磨砂玻璃 + 点击轻微收缩 + 平滑伸缩（点击标题栏展开 / 收起）。
 * 标题栏右侧可放附加内容（[headerRight]，如「历史订单」），末尾是一个随展开旋转的箭头。
 *
 * @param expanded    是否展开
 * @param onToggle    点击整卡切换展开 / 收起
 * @param headerRight 标题与箭头之间的附加内容（可选）
 * @param content     展开时显示的内容
 */
@Composable
private fun DetailCard(
    backdrop: Backdrop,
    inPreview: Boolean,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    headerRight: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 箭头旋转：收起指向右，展开旋转 90° 指向下
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "detailCardChevron"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 统一按压反馈（按下轻微收缩）+ 点击切换展开 / 收起
            .pressable(onClick = onToggle)
            .clip(RoundedCornerShape(24.dp))
            .frostedSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(24.dp),
                inPreview = inPreview
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            headerRight?.invoke(this)
            Spacer(modifier = Modifier.width(6.dp))
            Image(
                imageVector = MiuixIcons.Regular.ChevronForward,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = chevron },
                colorFilter = ColorFilter.tint(
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )
        }
        // 平滑伸缩：展开 / 收起时内容高度从 0 渐变到满，带动卡片上下边框平滑拉长，不闪现
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(240)
            ),
            exit = fadeOut(tween(160)) + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(200)
            )
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

/** 一行「标签 + 取值」信息：标签居左淡显，取值紧随其后，过长省略。 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 基础信息卡片：居左多行显示设备型号 / 待完成表单数量 / 在测样品批数 / 上次运行时间，
 * 右下角显示设备照片，照片水平宽度占卡片（内容区）宽度的五分之一；无照片时用占位框。
 */
@Composable
private fun BasicInfoCard(
    device: Device,
    backdrop: Backdrop,
    inPreview: Boolean,
) {
    val bitmap = rememberImageBitmap(device.imageUri)
    // 基础信息不做展开：常驻磨砂卡片，无箭头、不可点击
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 统一按压反馈（基础信息不做展开，仅按压反馈）
            .pressable(onClick = { })
            .clip(RoundedCornerShape(24.dp))
            .frostedSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(24.dp),
                inPreview = inPreview
            )
            .padding(16.dp)
    ) {
        Text(
            text = "基础信息",
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(12.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 照片水平宽度 = 内容宽度的 1/5
            val photoW = maxWidth / 5
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 给右下角照片让出横向空间，避免文字压到照片上
                    .padding(end = photoW + 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoRow("设备型号", device.model.ifBlank { "—" })
                InfoRow("待完成表单", "${device.pendingFormCount} 份")
                InfoRow("在测样品批数", "${device.testingSampleBatches} 批")
                InfoRow("上次运行时间", device.lastRunTime.ifBlank { "—" })
            }
            if (bitmap != null) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                Image(
                    bitmap = bitmap,
                    contentDescription = "设备照片",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(photoW)
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(photoW)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        imageVector = MiuixIcons.Regular.Photos,
                        contentDescription = "暂无照片",
                        modifier = Modifier.size(photoW * 0.45f),
                        colorFilter = ColorFilter.tint(
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    )
                }
            }
        }
    }
}

/**
 * 关联订单卡片：
 * - 使用中：展开显示当前正在测试的订单，每条一行——左侧订单号 + 样品批次，右侧入箱 / 出箱日期；
 * - 空闲：标题栏右侧显示「历史订单」，默认收起，点击展开列表式显示历史测过的订单号。
 */
@Composable
private fun AssociatedOrdersCard(
    device: Device,
    backdrop: Backdrop,
    inPreview: Boolean,
) {
    val inUse = device.status == DeviceStatus.IN_USE
    // 使用中默认展开当前订单；空闲默认收起，点「历史订单」再展开
    var expanded by remember(device.id) { mutableStateOf(inUse) }
    DetailCard(
        backdrop = backdrop,
        inPreview = inPreview,
        title = "关联订单",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        headerRight = {
            if (!inUse) {
                Text(
                    text = "历史订单",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
    ) {
        if (inUse) {
            if (device.currentOrders.isEmpty()) {
                EmptyHint("暂无正在测试的订单")
            } else {
                device.currentOrders.forEachIndexed { index, order ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        CardDivider()
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    OrderRow(order)
                }
            }
        } else {
            if (device.historyOrders.isEmpty()) {
                EmptyHint("暂无历史订单")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    device.historyOrders.forEach { no ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.6f))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = no,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单条关联订单：左侧订单号 + 样品批次，右侧入箱 / 出箱日期（双行）。 */
@Composable
private fun OrderRow(order: DeviceOrder) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = order.orderNo,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "批次 ${order.sampleBatch}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "入箱 ${order.boxInDate}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Text(
                text = "出箱 ${order.boxOutDate}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
        }
    }
}

/**
 * 表单情况卡片：顶部完成率进度条，下方多行显示待完成 / 已完成 / 总数 / 完成率。
 */
@Composable
private fun FormStatusCard(
    device: Device,
    backdrop: Backdrop,
    inPreview: Boolean,
) {
    var expanded by remember(device.id) { mutableStateOf(true) }
    val total = device.totalFormCount
    val done = device.completedFormCount
    val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).toInt()
    DetailCard(
        backdrop = backdrop,
        inPreview = inPreview,
        title = "表单情况",
        expanded = expanded,
        onToggle = { expanded = !expanded }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 完成率进度条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MiuixTheme.colorScheme.primary)
                )
            }
            InfoRow("待完成表单", "${device.pendingFormCount} 份")
            InfoRow("已完成表单", "$done 份")
            InfoRow("表单总数", "$total 份")
            InfoRow("完成率", "$percent%")
        }
    }
}

/**
 * 更多信息卡片：设备编号 / 序号 / 所属地图 / 网格位置 / 运行状态。
 */
@Composable
private fun MoreInfoCard(
    device: Device,
    store: DeviceStore,
    backdrop: Backdrop,
    inPreview: Boolean,
) {
    var expanded by remember(device.id) { mutableStateOf(true) }
    val map = store.maps.firstOrNull { it.id == device.mapId }
    val mapName = map?.name ?: "—"
    // 网格位置：由 cell 与地图列数反算行列（1 起）
    val position = if (map != null && map.cols > 0) {
        val row = device.cell / map.cols + 1
        val col = device.cell % map.cols + 1
        "第 $row 行 · 第 $col 列"
    } else {
        "—"
    }
    val statusText = if (device.status == DeviceStatus.IN_USE) "使用中" else "空闲"
    val seq = extractSequence(device.number)
    DetailCard(
        backdrop = backdrop,
        inPreview = inPreview,
        title = "更多信息",
        expanded = expanded,
        onToggle = { expanded = !expanded }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow("设备编号", device.number.ifBlank { "—" })
            InfoRow("设备序号", seq ?: "未编号")
            InfoRow("所属地图", mapName)
            InfoRow("网格位置", position)
            InfoRow("运行状态", statusText)
        }
    }
}

/** 卡片内分隔线 */
@Composable
private fun CardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    )
}

/** 空数据占位提示 */
@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}
