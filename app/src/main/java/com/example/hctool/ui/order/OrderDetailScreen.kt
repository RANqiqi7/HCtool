package com.example.hctool.ui.order

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hctool.R
import com.example.hctool.ui.components.glassSurface
import com.example.hctool.ui.device.CardDivider
import com.example.hctool.ui.device.DetailCard
import com.example.hctool.ui.device.Device
import com.example.hctool.ui.device.DeviceCard
import com.example.hctool.ui.device.DeviceStore
import com.example.hctool.ui.device.EmptyHint
import com.example.hctool.ui.device.GlassIconButton
import com.example.hctool.ui.device.InfoRow
import com.example.hctool.ui.device.StaticCard
import com.example.hctool.ui.device.pressable
import com.example.hctool.ui.device.rememberImageBitmap
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxColors
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 订单详情页：点击订单列表里的卡片打开，布局结构照抄设备详情页。
 *
 * 结构（自上而下）：
 * - 悬浮顶栏：左上返回按钮、右上编辑按钮（玻璃按钮，带按压轻微收缩），
 *   中间悬浮标题胶囊——左侧订单号、右侧订单状态（进行中 / 已完结）；
 * - 基础信息卡片：居左多行显示客户代称 / 样品名称 / 样品数量 / 样品编号 / 测试条件 / Case Owner
 *   （为空的字段不显示），右下角留图片展示位；
 * - 完成进度卡片：标题右侧是完成度百分比进度环（满为绿色、未满为蓝色），
 *   卡内是列表式节点状态（IQC → 测试 → OQC），节点按顺序进行，用分割线隔开。
 *
 * 整页从右侧滑入，返回时先滑出再移除；详情页自绘一张同款背景图，
 * 既遮住下层订单页，又与玻璃采样源（imageBackdrop）对齐，保证磨砂折射一致。
 *
 * @param order        打开详情时的订单快照；节点/表单状态存在 [store] 里，页面内始终读活数据
 * @param store        订单数据（勾选节点/表单后写回，界面自动刷新）
 * @param deviceStore  设备数据（用于反查关联了本订单的设备，展示「关联设备」卡片）
 * @param backdrop     玻璃采样源（用 imageBackdrop，只采背景图）
 * @param inPreview    预览态退化玻璃为纯色
 * @param onBack       返回（滑出动画结束后回调）
 * @param onEdit       编辑按钮点击（具体编辑逻辑待接入）
 * @param onDeviceClick 点关联设备卡片（上抛设备，由 MainActivity 打开对应设备详情页）
 */
@Composable
fun OrderDetailScreen(
    order: Order,
    store: OrderStore,
    deviceStore: DeviceStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeviceClick: (Device) -> Unit,
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

    // 节点勾选写回 store，这里按 id 取活数据，保证进度环与节点状态实时刷新
    val current = store.orders.firstOrNull { it.id == order.id } ?: order
    // 关联设备：当前订单或历史订单里含本订单号的设备
    val relatedDevices = deviceStore.devices.filter { d ->
        d.currentOrders.any { it.orderNo == current.orderNo } ||
            d.historyOrders.any { it.orderNo == current.orderNo }
    }

    // 详情内容采样层：记录下方卡片，供悬浮顶栏玻璃采样（顶栏在该层之外，不会自采样）
    val detailContentBackdrop = rememberLayerBackdrop { drawContent() }
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, detailContentBackdrop)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenW = maxWidth
        val slide by animateDpAsState(
            targetValue = if (shown) 0.dp else screenW,
            animationSpec = tween(durationMillis = 300),
            label = "orderDetailSlide"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 用布局 offset 驱动整屏滑入 / 滑出，保证内部磨砂卡片玻璃边框一起移动
                .offset(x = slide)
                // 吃掉落在空白处的点击，避免穿透到下层订单页
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            // 自绘背景图：遮住下层订单页；预览态退化为纯色
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
                    order = current,
                    backdrop = backdrop,
                    inPreview = inPreview
                )
                AssociatedDevicesCard(
                    devices = relatedDevices,
                    orderStore = store,
                    backdrop = backdrop,
                    inPreview = inPreview,
                    onDeviceClick = onDeviceClick
                )
                ProgressCard(
                    order = current,
                    store = store,
                    backdrop = backdrop,
                    inPreview = inPreview
                )
                FormCard(
                    order = current,
                    store = store,
                    backdrop = backdrop,
                    inPreview = inPreview
                )
            }
            // 悬浮顶栏浮在滚动内容之上：返回 + 标题胶囊(订单号 + 订单状态) + 编辑
            DetailTopBar(
                order = current,
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
 * 中间是悬浮标题胶囊——左侧订单号，右侧订单状态（进行中蓝 / 已完结绿）。
 * 整条顶栏固定不随内容滚动。
 */
@Composable
private fun DetailTopBar(
    order: Order,
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
        // 悬浮标题胶囊：订单号居左 + 订单状态居右（glassSurface 通透玻璃，非磨砂卡片）
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
                    text = order.orderNo,
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = order.status.label(),
                    style = MiuixTheme.textStyles.body2,
                    color = order.status.color(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
 * 基础信息卡片：居左多行显示订单的客户代称 / 样品名称 / 样品数量 / 样品编号 / 测试条件 / Case Owner，
 * 选填字段为空则整行不显示；右下角显示订单图片，
 * 图片水平宽度占卡片（内容区）宽度的五分之一，无图片时用占位框。
 */
@Composable
private fun BasicInfoCard(
    order: Order,
    backdrop: Backdrop,
    inPreview: Boolean,
) {
    val bitmap = rememberImageBitmap(order.imageUri)
    // 标签 + 取值成对收集，取值为空的直接丢掉（客户代称必填，一定在）
    val fields = listOf(
        "客户代称" to order.customer,
        "样品名称" to order.sampleName,
        "样品数量" to order.sampleCount,
        "样品编号" to order.sampleNo,
        "测试条件" to order.testCondition,
        "Case Owner" to order.caseOwner,
    ).filter { it.second.isNotBlank() }

    // 基础信息不做展开：常驻磨砂卡片，无箭头、不可点击
    StaticCard(backdrop = backdrop, inPreview = inPreview) {
        Text(
            text = "基础信息",
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(12.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 图片水平宽度 = 内容宽度的 1/5
            val photoW = maxWidth / 5
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 给右下角图片让出横向空间，避免文字压到图片上
                    .padding(end = photoW + 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (fields.isEmpty()) {
                    EmptyHint("暂无基础信息")
                } else {
                    fields.forEach { (label, value) -> InfoRow(label, value) }
                }
            }
            if (bitmap != null) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                Image(
                    bitmap = bitmap,
                    contentDescription = "订单图片",
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
                        contentDescription = "暂无图片",
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
 * 关联设备卡片：列出关联了本订单的设备（当前订单或历史订单含本订单号），
 * 每台设备的展示与首页设备列表里的设备卡片完全一致（复用 DeviceCard）；
 * 点击某台设备即进入对应设备详情页。无关联设备时显示占位提示。
 */
@Composable
private fun AssociatedDevicesCard(
    devices: List<Device>,
    orderStore: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    onDeviceClick: (Device) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    DetailCard(
        backdrop = backdrop,
        inPreview = inPreview,
        title = "关联设备",
        expanded = expanded,
        onToggle = { expanded = !expanded }
    ) {
        if (devices.isEmpty()) {
            EmptyHint("暂无关联设备")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                devices.forEach { device ->
                    DeviceCard(
                        device = device,
                        orderStore = orderStore,
                        backdrop = backdrop,
                        inPreview = inPreview,
                        onClick = onDeviceClick
                    )
                }
            }
        }
    }
}

/**
 * 完成进度卡片：主标题「完成进度」，标题右侧是完成度百分比进度环——
 * 百分比满为绿色整圆环，未满为蓝色不完整圆环。
 * 卡内是列表式的节点状态（IQC → 测试 → OQC），节点之间用分割线隔开。
 */
@Composable
private fun ProgressCard(
    order: Order,
    store: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
) {
    var expanded by remember(order.id) { mutableStateOf(true) }
    val nodes = order.nodes
    DetailCard(
        backdrop = backdrop,
        inPreview = inPreview,
        title = "完成进度",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        headerRight = {
            ProgressRing(fraction = nodes.completedFraction())
            Spacer(modifier = Modifier.width(6.dp))
        }
    ) {
        if (nodes.isEmpty()) {
            EmptyHint("暂无节点")
        } else {
            nodes.forEachIndexed { index, node ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    CardDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                }
                NodeRow(
                    node = node,
                    // 节点按顺序进行：前一个节点没完成时，后面的 checkbox 不可点
                    enabled = index == 0 || nodes[index - 1].state == OrderNodeState.DONE,
                    onClick = { store.advanceNode(order.id, index) }
                )
            }
        }
    }
}

/**
 * 完成度进度环：环内居中显示百分比数字。
 * 满 100% 走已完结的绿色整圆环，未满走进行中的蓝色不完整圆环。
 */
@Composable
private fun ProgressRing(fraction: Float) {
    val clamped = fraction.coerceIn(0f, 1f)
    val percent = (clamped * 100).roundToInt()
    val ringColor = if (clamped >= 1f) OrderCompletedColor else OrderInProgressColor
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = clamped,
            size = 44.dp,
            strokeWidth = 4.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = ringColor,
                backgroundColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            )
        )
        Text(
            text = "$percent%",
            style = MiuixTheme.textStyles.footnote2,
            color = ringColor,
            maxLines = 1
        )
    }
}

/**
 * 单个节点行：左侧节点名，点过之后下面跟上开始 / 结束时间；右侧是三态 checkbox。
 * 空白 = 未开始，半选 = 进行中（记开始时间），选中 = 已完成（再记结束时间）。
 */
@Composable
private fun NodeRow(
    node: OrderNode,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val onSurface = MiuixTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = node.name,
                style = MiuixTheme.textStyles.body1,
                // 轮不到的节点整行淡显，和不可点的 checkbox 呼应
                color = if (enabled) onSurface else onSurface.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (node.startTime.isNotBlank()) {
                Text(
                    text = "开始 ${node.startTime}",
                    style = MiuixTheme.textStyles.body2,
                    color = onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (node.endTime.isNotBlank()) {
                Text(
                    text = "结束 ${node.endTime}",
                    style = MiuixTheme.textStyles.body2,
                    color = onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Checkbox(
            state = node.state.toggleable(),
            onClick = onClick,
            enabled = enabled,
            colors = orderCheckboxColors()
        )
    }
}

/**
 * 表单进度卡片：与完成进度同一套逻辑——主标题「表单进度」，标题右侧完成度百分比环，
 * 卡内列表式表单（使用记录表 / runcard），表单之间用分割线隔开。
 * 区别是表单 checkbox 只有未选 / 选中两态，勾选时记一个填写时间显示在表单名下方。
 */
@Composable
private fun FormCard(
    order: Order,
    store: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
) {
    var expanded by remember(order.id) { mutableStateOf(true) }
    val forms = order.forms
    DetailCard(
        backdrop = backdrop,
        inPreview = inPreview,
        title = "表单进度",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        headerRight = {
            ProgressRing(fraction = forms.filledFraction())
            Spacer(modifier = Modifier.width(6.dp))
        }
    ) {
        if (forms.isEmpty()) {
            EmptyHint("暂无表单")
        } else {
            forms.forEachIndexed { index, form ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    CardDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                }
                FormRow(
                    form = form,
                    onClick = { store.toggleForm(order.id, index) }
                )
            }
        }
    }
}

/**
 * 单张表单行：左侧表单名，勾选后下面跟一个填写时间；右侧是两态 checkbox（未选 / 选中）。
 * 表单之间没有先后顺序，任意一张都可独立勾选 / 撤销。
 */
@Composable
private fun FormRow(
    form: OrderForm,
    onClick: () -> Unit,
) {
    val onSurface = MiuixTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = form.name,
                style = MiuixTheme.textStyles.body1,
                color = onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (form.fillTime.isNotBlank()) {
                Text(
                    text = "填写 ${form.fillTime}",
                    style = MiuixTheme.textStyles.body2,
                    color = onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Checkbox(
            state = if (form.filled) ToggleableState.On else ToggleableState.Off,
            onClick = onClick,
            colors = orderCheckboxColors()
        )
    }
}

/**
 * 订单详情页 checkbox 配色：磨砂卡片底色偏深，
 * 未选中态用半透明白描出空心圈，选中态沿用主题色，禁用态整体淡化。
 * 节点与表单共用同一套，保证观感一致。
 */
@Composable
private fun orderCheckboxColors(): CheckboxColors = CheckboxDefaults.checkboxColors(
    checkedForegroundColor = Color.White,
    uncheckedForegroundColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f),
    disabledCheckedForegroundColor = Color.White.copy(alpha = 0.55f),
    disabledUncheckedForegroundColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    checkedBackgroundColor = MiuixTheme.colorScheme.primary,
    uncheckedBackgroundColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.2f),
    disabledCheckedBackgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.35f),
    disabledUncheckedBackgroundColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
)

/** 节点状态 → checkbox 三态：未开始空白、进行中半选、已完成选中 */
private fun OrderNodeState.toggleable(): ToggleableState = when (this) {
    OrderNodeState.NOT_STARTED -> ToggleableState.Off
    OrderNodeState.IN_PROGRESS -> ToggleableState.Indeterminate
    OrderNodeState.DONE -> ToggleableState.On
}
