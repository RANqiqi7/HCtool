package com.example.hctool.ui.order

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hctool.R
import com.example.hctool.ui.components.BiometricVerify
import com.example.hctool.ui.components.glassSurface
import com.example.hctool.ui.components.AppBackgroundImage
import com.example.hctool.ui.components.rememberBiometricVerifier
import com.example.hctool.ui.data.ImageStore
import com.example.hctool.ui.device.CardDivider
import com.example.hctool.ui.device.DetailCard
import com.example.hctool.ui.device.Device
import com.example.hctool.ui.device.DeviceStatus
import com.example.hctool.ui.device.DeviceStore
import com.example.hctool.ui.device.EditRow
import com.example.hctool.ui.device.EmptyHint
import com.example.hctool.ui.device.GlassIconButton
import com.example.hctool.ui.device.InfoRow
import com.example.hctool.ui.device.StaticCard
import com.example.hctool.ui.device.StatusDot
import com.example.hctool.ui.device.StatusIdleColor
import com.example.hctool.ui.device.StatusInUseColor
import com.example.hctool.ui.device.computeStatus
import com.example.hctool.ui.device.extractSequence
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
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Scan
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
    onDeviceClick: (Device) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
    // 节点勾选写回 store，这里按 id 取活数据，保证进度环与节点状态实时刷新
    val current = store.orders.firstOrNull { it.id == order.id } ?: order

    // 编辑模式：进入时把基础信息拷成草稿，点右上角对号才写回 store，点叉直接丢弃
    var editing by remember { mutableStateOf(false) }
    var customerDraft by remember { mutableStateOf("") }
    var sampleNameDraft by remember { mutableStateOf("") }
    var sampleCountDraft by remember { mutableStateOf("") }
    var sampleNoDraft by remember { mutableStateOf("") }
    var testConditionDraft by remember { mutableStateOf("") }
    var caseOwnerDraft by remember { mutableStateOf("") }
    var imageDraft by remember { mutableStateOf<String?>(null) }
    // 客户代称必填，与新增订单弹窗同一规则：清空后对号置灰、点了也不保存
    val canSave = customerDraft.isNotBlank()
    // 开始测试弹窗开关：从测试节点「未开始→进行中」时弹出（填时间 + 选设备）
    var startTestOpen by remember { mutableStateOf(false) }

    // 相册：系统图片选择器；一次性临时授权必须当场拷成自家永久副本，否则重启读不出来
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) scope.launch {
            imageDraft = ImageStore.persistPicked(context, uri) ?: uri.toString()
        }
    }
    // 相机：拍照直接落在永久目录，再经 FileProvider 授权给相机 App 写入
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingPhotoUri?.let { imageDraft = it.toString() }
    }

    fun launchCamera() {
        val uri = ImageStore.newCameraTarget(context)
        pendingPhotoUri = uri
        runCatching { cameraLauncher.launch(uri) }
    }

    fun pickFromGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun enterEdit() {
        customerDraft = current.customer
        sampleNameDraft = current.sampleName
        sampleCountDraft = current.sampleCount
        sampleNoDraft = current.sampleNo
        testConditionDraft = current.testCondition
        caseOwnerDraft = current.caseOwner
        imageDraft = current.imageUri
        editing = true
    }
    fun saveEdit() {
        if (!canSave) return
        store.updateBasicInfo(
            id = current.id,
            customer = customerDraft.trim(),
            sampleName = sampleNameDraft.trim(),
            sampleCount = sampleCountDraft.trim(),
            sampleNo = sampleNoDraft.trim(),
            testCondition = testConditionDraft.trim(),
            caseOwner = caseOwnerDraft.trim(),
            imageUri = imageDraft
        )
        editing = false
    }
    // 取消编辑：连草稿一起丢弃，不写回 store
    fun cancelEdit() {
        editing = false
    }
    // 系统返回键：编辑态先退出编辑，否则等同点返回按钮
    // （放在草稿与 cancelEdit 之后声明：局部 by delegate 变量不允许前置引用）
    BackHandler { if (editing) cancelEdit() else requestBack() }
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
            AppBackgroundImage(modifier = Modifier.fillMaxSize(), inPreview = inPreview)

            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // 编辑态弹输入法时取两者较大值作为底部留白，
            // 既不重复叠加导航栏高度，也能把被键盘挡住的内容滚出来
            val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(detailContentBackdrop)
                    .verticalScroll(rememberScrollState())
                    // 首屏让开悬浮顶栏（状态栏 + 12 + 顶栏高 56 + 间距 16）；
                    // 滚动区铺满全屏，上滑时卡片可穿过顶栏下方，贯通无裁剪横线
                    .padding(top = statusTop + 12.dp + 56.dp + 16.dp)
                    .padding(bottom = maxOf(navBottom, imeBottom) + 24.dp)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BasicInfoCard(
                    order = current,
                    backdrop = backdrop,
                    inPreview = inPreview,
                    editing = editing,
                    customer = customerDraft,
                    onCustomerChange = { customerDraft = it },
                    sampleName = sampleNameDraft,
                    onSampleNameChange = { sampleNameDraft = it },
                    sampleCount = sampleCountDraft,
                    onSampleCountChange = { sampleCountDraft = it },
                    sampleNo = sampleNoDraft,
                    onSampleNoChange = { sampleNoDraft = it },
                    testCondition = testConditionDraft,
                    onTestConditionChange = { testConditionDraft = it },
                    caseOwner = caseOwnerDraft,
                    onCaseOwnerChange = { caseOwnerDraft = it },
                    imageUri = if (editing) imageDraft else current.imageUri,
                    onCamera = { launchCamera() },
                    onGallery = { pickFromGallery() }
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
                    inPreview = inPreview,
                    onStartTest = { startTestOpen = true }
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
                editing = editing,
                canSave = canSave,
                onLeftClick = { if (editing) cancelEdit() else requestBack() },
                onRightClick = { if (editing) saveEdit() else enterEdit() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = statusTop + 12.dp)
                    .padding(horizontal = 20.dp)
            )
        }
        // 开始测试弹窗：从测试节点未开始→进行中时弹出，叠在详情页之上
        if (startTestOpen) {
            StartTestDialog(
                backdrop = backdrop,
                inPreview = inPreview,
                devices = deviceStore.devices,
                orderNo = current.orderNo,
                onDismiss = { startTestOpen = false },
                onConfirm = { device, hours ->
                    deviceStore.bindDeviceToOrder(
                        deviceId = device.id,
                        orderNo = current.orderNo,
                        sampleBatch = current.sampleNo,
                        startTime = currentNodeTime()
                    )
                    store.startTest(current.id, hours)
                    startTestOpen = false
                }
            )
        }
    }
}

/**
 * 详情页悬浮顶栏：左返回、右编辑（玻璃图标按钮，自带按压缩放），
 * 中间是悬浮标题胶囊——左侧订单号，右侧订单状态（进行中蓝 / 已完结绿）。
 * 编辑态下两个按钮换语义：左＝叉（取消编辑、丢弃草稿），右＝对号（保存写回 store）；
 * 客户代称被清空时对号置灰，点了也不保存。
 * 整条顶栏固定不随内容滚动。
 */
@Composable
private fun DetailTopBar(
    order: Order,
    backdrop: Backdrop,
    inPreview: Boolean,
    editing: Boolean,
    canSave: Boolean,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(
            backdrop = backdrop,
            icon = if (editing) MiuixIcons.Regular.Close else MiuixIcons.Regular.Back,
            contentDescription = if (editing) "取消编辑" else "返回",
            onClick = onLeftClick,
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
            icon = if (editing) MiuixIcons.Regular.Ok else MiuixIcons.Regular.Edit,
            contentDescription = if (editing) "保存" else "编辑",
            onClick = onRightClick,
            // 编辑态但必填项为空：对号置灰提示不可保存
            tint = if (editing && !canSave)
                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            else
                MiuixTheme.colorScheme.primary,
            size = 56.dp
        )
    }
}

/**
 * 基础信息卡片：居左多行显示订单的客户代称 / 样品名称 / 样品数量 / 样品编号 / 测试条件 / Case Owner，
 * 选填字段为空则整行不显示；右下角显示订单图片，
 * 图片水平宽度占卡片（内容区）宽度的五分之一，无图片时用占位框。
 * 编辑态下六个字段全量换成输入框（包括原本为空的选填项），
 * 左侧列末尾多出拍照 / 相册两个按钮，右下角预览跟着草稿走。
 */
@Composable
private fun BasicInfoCard(
    order: Order,
    backdrop: Backdrop,
    inPreview: Boolean,
    editing: Boolean,
    customer: String,
    onCustomerChange: (String) -> Unit,
    sampleName: String,
    onSampleNameChange: (String) -> Unit,
    sampleCount: String,
    onSampleCountChange: (String) -> Unit,
    sampleNo: String,
    onSampleNoChange: (String) -> Unit,
    testCondition: String,
    onTestConditionChange: (String) -> Unit,
    caseOwner: String,
    onCaseOwnerChange: (String) -> Unit,
    imageUri: String?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    val bitmap = rememberImageBitmap(imageUri)
    // 测试完成后，把开始测试时填写的预计小时数换算成「X天Y小时」记入基础信息
    val testDuration = formatTestDuration(order.testHours)
    val testDone = order.nodes.firstOrNull { it.name == "测试" }?.state == OrderNodeState.DONE
    // 标签 + 取值成对收集，取值为空的直接丢掉（客户代称必填，一定在）
    val fields = buildList {
        add("客户代称" to order.customer)
        add("样品名称" to order.sampleName)
        add("样品数量" to order.sampleCount)
        add("样品编号" to order.sampleNo)
        add("测试条件" to order.testCondition)
        add("Case Owner" to order.caseOwner)
        if (testDone && testDuration.isNotBlank()) add("测试时间" to testDuration)
    }.filter { it.second.isNotBlank() }

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
                if (editing) {
                    // 六行全量列出，原本没填的选填项也要能补上
                    EditRow("客户代称", customer, onCustomerChange)
                    EditRow("样品名称", sampleName, onSampleNameChange)
                    EditRow("样品数量", sampleCount, onSampleCountChange, keyboardType = KeyboardType.Number)
                    EditRow("样品编号", sampleNo, onSampleNoChange, keyboardType = KeyboardType.Ascii)
                    EditRow("测试条件", testCondition, onTestConditionChange)
                    EditRow("Case Owner", caseOwner, onCaseOwnerChange)
                    // 换图：与新增订单弹窗同款图标（拍照 / 相册）
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassIconButton(
                            backdrop = backdrop,
                            icon = MiuixIcons.Regular.Scan,
                            contentDescription = "拍照",
                            onClick = onCamera,
                            frosted = true
                        )
                        GlassIconButton(
                            backdrop = backdrop,
                            icon = MiuixIcons.Regular.Photos,
                            contentDescription = "相册",
                            onClick = onGallery,
                            frosted = true
                        )
                    }
                } else if (fields.isEmpty()) {
                    EmptyHint("暂无基础信息")
                } else {
                    // 测试条件可能很长：整段多行换行显示，不省略截断
                    fields.forEach { (label, value) ->
                        InfoRow(label, value, multiLine = label == "测试条件")
                    }
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
 * 关联设备卡片：列出关联了本订单的设备（当前订单或历史订单含本订单号）。
 * 行样式采用与关联订单一样的逻辑：设备卡片不带外框，状态色球居左垂直居中，
 * 右侧两行（设备名 / 序号）水平起始位置对齐，多台设备之间用分割线隔开；
 * 点击某台设备即进入对应设备详情页。常驻展开，不带下拉 / 收起。
 */
@Composable
private fun AssociatedDevicesCard(
    devices: List<Device>,
    orderStore: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    onDeviceClick: (Device) -> Unit,
) {
    StaticCard(backdrop = backdrop, inPreview = inPreview) {
        Text(
            text = "关联设备",
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (devices.isEmpty()) {
            EmptyHint("暂无关联设备")
        } else {
            devices.forEachIndexed { index, device ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    CardDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                }
                DeviceRow(
                    device = device,
                    orderStore = orderStore,
                    onDeviceClick = onDeviceClick
                )
            }
        }
    }
}

/**
 * 单条关联设备行：与关联订单行同款布局——
 * 左侧状态色球（使用中蓝 / 空闲绿）垂直居中，右侧两行内容同一起始位置。
 */
@Composable
private fun DeviceRow(
    device: Device,
    orderStore: OrderStore,
    onDeviceClick: (Device) -> Unit,
) {
    val inUse = device.computeStatus(orderStore) == DeviceStatus.IN_USE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = { onDeviceClick(device) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color = if (inUse) StatusInUseColor else StatusIdleColor)
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = device.nickname,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = extractSequence(device.number)?.let { "序号 $it" } ?: "未编号",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    onStartTest: () -> Unit,
) {
    val nodes = order.nodes
    // 改节点状态属于关键操作，先过生物验证，通过才写回
    val verify = rememberBiometricVerifier()
    DetailCard(
        backdrop = backdrop,
        inPreview = inPreview,
        title = "完成进度",
        // 不带下拉展开：节点列表始终完整显示
        collapsible = false,
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
                    // 测试进行中：开始时间下面一行展示「预计结束」= 开始 + 填写的小时数
                    expectedEnd = if (node.name == "测试" && node.state == OrderNodeState.IN_PROGRESS) {
                        expectedEndTime(node.startTime, order.testHours)
                    } else {
                        ""
                    },
                    onClick = {
                        // 生物验证通过才改节点状态，取消 / 失败不动数据
                        verify("验证通过后才能修改节点状态") { passed ->
                            if (passed) {
                                // 测试节点从「未开始」起步时先弹窗（填时间 + 选设备），
                                // 由弹窗内确认才真正启动；其余状态切换走原有逐格推进
                                if (node.name == "测试" && node.state == OrderNodeState.NOT_STARTED) {
                                    onStartTest()
                                } else {
                                    store.advanceNode(order.id, index)
                                }
                            }
                        }
                    }
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
 * 行高用 animateContentSize 平滑过渡：新增一行时间时，本行高度渐变拉长，
 * 下方节点与分割线跟着往下滑，而不是瞬间跳位。
 */
@Composable
private fun NodeRow(
    node: OrderNode,
    enabled: Boolean,
    expectedEnd: String,
    onClick: () -> Unit,
) {
    val onSurface = MiuixTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 只让 checkbox 响应点击（整行可点会绕过「前一节点未完成则锁定」的限制）
            .animateContentSize(animationSpec = tween(durationMillis = 240)),
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
            } else if (expectedEnd.isNotBlank()) {
                // 测试进行中：未记真实结束时间前，先按开始 + 预计时长显示预计结束
                Text(
                    text = "预计结束 $expectedEnd",
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
    val forms = order.forms
    DetailCard(
        backdrop = backdrop,
        inPreview = inPreview,
        title = "表单进度",
        // 不带下拉展开：表单列表始终完整显示
        collapsible = false,
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
 * 表单之间没有先后顺序，任意一张都可独立勾选 / 撤销；行高同样平滑过渡。
 */
@Composable
private fun FormRow(
    form: OrderForm,
    onClick: () -> Unit,
) {
    val onSurface = MiuixTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 只让 checkbox 响应点击，与节点行保持一致
            .animateContentSize(animationSpec = tween(durationMillis = 240)),
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
