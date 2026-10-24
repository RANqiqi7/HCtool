package com.example.hctool.ui.device

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hctool.R
import com.example.hctool.ui.components.glassSurface
import com.example.hctool.ui.order.OrderStatus
import com.example.hctool.ui.order.OrderStore
import com.example.hctool.ui.order.status
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
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
 * @param onBack       返回（滑出动画结束后回调）
 * @param onEdit       编辑按钮点击（具体编辑逻辑待接入）
 * @param onOrderClick 点关联订单里的某一条（上抛订单号，由 MainActivity 打开对应订单详情页）
 */
@Composable
fun DeviceDetailScreen(
    device: Device,
    store: DeviceStore,
    orderStore: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOrderClick: (String) -> Unit,
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
                    orderStore = orderStore,
                    backdrop = backdrop,
                    inPreview = inPreview,
                    onOrderClick = onOrderClick
                )
                FormStatusCard(
                    device = device,
                    backdrop = backdrop,
                    inPreview = inPreview
                )
                MoreInfoCard(
                    device = device,
                    store = store,
                    orderStore = orderStore,
                    backdrop = backdrop,
                    inPreview = inPreview
                )
            }
            // 悬浮顶栏浮在滚动内容之上：返回 + 标题胶囊(设备名+编号+状态圆点) + 编辑
            DetailTopBar(
                device = device,
                orderStore = orderStore,
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
    orderStore: OrderStore,
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
                // 胶囊右侧只展示序号（从设备编号正则提取），完整编号在「更多信息」卡片里看
                Text(
                    text = extractSequence(device.number)?.let { "序号 $it" } ?: "未编号",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusDot(status = device.computeStatus(orderStore), size = 10.dp)
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
 * 基础信息卡片：居左多行显示设备型号 / 待完成表单数量 / 在测样品批数 / 上次运行时间。
 * 设备照片已移到「更多信息」卡片里。
 */
@Composable
private fun BasicInfoCard(
    device: Device,
    backdrop: Backdrop,
    inPreview: Boolean,
) {
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow("设备型号", device.model.ifBlank { "—" })
            InfoRow("待完成表单", "${device.pendingFormCount} 份")
            InfoRow("在测样品批数", "${device.testingSampleBatches} 批")
            InfoRow("上次运行时间", device.lastRunTime.ifBlank { "—" })
        }
    }
}

/**
 * 关联订单卡片：
 * - 始终常驻展开第一条订单（收起态卡片不空），再点一次展开剩余全部订单；
 * - 使用中展示当前正在测试的订单（订单号 + 样品批次 + 开始 / 结束时间），
 *   空闲展示历史测过的订单，两者行格式一致；
 * - 开始 / 结束时间定向到该订单的 IQC 开始时间 / OQC 结束时间；
 * - 订单号前一个状态圆点：进行中蓝 / 已完结绿（与设备状态圆点同款）；
 * - 不同订单之间用分割线隔开；整行可点进对应订单详情页。
 */
@Composable
private fun AssociatedOrdersCard(
    device: Device,
    orderStore: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    onOrderClick: (String) -> Unit,
) {
    val inUse = device.computeStatus(orderStore) == DeviceStatus.IN_USE
    // 默认只展开第一条；点一次再展开剩余全部
    var showAll by remember(device.id) { mutableStateOf(false) }
    // 统一成同一种行数据：开始 / 结束定向到该订单的 IQC 开始 / OQC 结束时间，
    // 订单不在订单库时回退到设备侧记录的 startTime / endTime
    fun entryOf(o: DeviceOrder, defaultCompleted: Boolean): OrderEntry {
        val linked = orderStore.findByOrderNo(o.orderNo)
        return OrderEntry(
            orderNo = o.orderNo,
            completed = linked?.status?.let { it == OrderStatus.COMPLETED } ?: defaultCompleted,
            sampleBatch = o.sampleBatch,
            startTime = linked?.nodes?.firstOrNull()?.startTime ?: o.startTime,
            endTime = linked?.nodes?.lastOrNull()?.endTime ?: o.endTime,
        )
    }
    val entries: List<OrderEntry> = if (inUse) {
        device.currentOrders.map { entryOf(it, defaultCompleted = false) }
    } else {
        // 历史订单与当前订单同构，行格式完全一致；库里查不到时按已完结处理（绿色圆点）
        device.historyOrders.map { entryOf(it, defaultCompleted = true) }
    }
    DetailCard(
        backdrop = backdrop,
        inPreview = inPreview,
        title = "关联订单",
        expanded = showAll,
        onToggle = { showAll = !showAll },
        // 收起态也常驻第一条订单
        previewContent = {
            if (entries.isEmpty()) {
                EmptyHint(if (inUse) "暂无正在测试的订单" else "暂无历史订单")
            } else {
                OrderEntryRow(entries.first(), onOrderClick)
            }
        }
    ) {
        // 展开后补上剩余订单，开头一道分割线与常驻的第一条隔开
        entries.drop(1).forEachIndexed { index, entry ->
            if (index == 0) {
                CardDivider()
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                CardDivider()
                Spacer(modifier = Modifier.height(10.dp))
            }
            OrderEntryRow(entry, onOrderClick)
        }
    }
}

/** 关联订单行数据：开始 / 结束取自订单 IQC 开始 / OQC 结束（不在库时回退设备侧记录） */
private data class OrderEntry(
    val orderNo: String,
    val completed: Boolean,
    val sampleBatch: String = "",
    val startTime: String = "",
    val endTime: String = "",
)

/**
 * 单条关联订单行：订单号前一个状态圆点（进行中蓝 / 已完结绿，与设备状态圆点同款），
 * 批次跟在名称下方，开始 / 结束时间在右侧；整行可点进对应订单详情页。
 */
@Composable
private fun OrderEntryRow(entry: OrderEntry, onOrderClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = { onOrderClick(entry.orderNo) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color = if (entry.completed) StatusIdleColor else StatusInUseColor)
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = entry.orderNo,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.sampleBatch.isNotBlank()) {
                Text(
                    text = "批次 ${entry.sampleBatch}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 开始 / 结束两行常驻，缺时间用 — 占位，保证不同设备的行格式一致
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "开始 ${entry.startTime.ifBlank { "—" }}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Text(
                text = "结束 ${entry.endTime.ifBlank { "—" }}",
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
    val total = device.totalFormCount
    val done = device.completedFormCount
    val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).toInt()
    DetailCard(
        backdrop = backdrop,
        inPreview = inPreview,
        title = "表单情况",
        // 不带下拉展开：内容始终完整显示
        collapsible = false
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
 * 更多信息卡片：设备编号 / 所属地图 / 运行状态，下方展示设备照片（已从基础信息卡片移到这里）；
 * 无照片时用占位框。
 */
@Composable
private fun MoreInfoCard(
    device: Device,
    store: DeviceStore,
    orderStore: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
) {
    val map = store.maps.firstOrNull { it.id == device.mapId }
    val mapName = map?.name ?: "—"
    val statusText = if (device.computeStatus(orderStore) == DeviceStatus.IN_USE) "使用中" else "空闲"
    val bitmap = rememberImageBitmap(device.imageUri)
    DetailCard(
        backdrop = backdrop,
        inPreview = inPreview,
        title = "更多信息",
        // 不带下拉展开：内容始终完整显示
        collapsible = false
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow("设备编号", device.number.ifBlank { "—" })
            InfoRow("所属地图", mapName)
            InfoRow("运行状态", statusText)
            // 照片整宽展示，高度随图片宽高比自适应
            if (bitmap != null) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                Image(
                    bitmap = bitmap,
                    contentDescription = "设备照片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.6f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        imageVector = MiuixIcons.Regular.Photos,
                        contentDescription = "暂无照片",
                        modifier = Modifier.size(36.dp),
                        colorFilter = ColorFilter.tint(
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    )
                }
            }
        }
    }
}
