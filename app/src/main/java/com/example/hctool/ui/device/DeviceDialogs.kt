package com.example.hctool.ui.device

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeAnimationTarget
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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.hctool.ui.components.GlassButton
import com.example.hctool.ui.components.frostedSurface
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 弹框背后的半透明遮罩 */
private val DialogScrim = Color.Black.copy(alpha = 0.35f)

/** 抓取到的输入法高度（取出现过的最大值并跨弹窗保留），用于固定弹窗底边的预留 */
private var RememberedImeHeight by mutableStateOf(0.dp)

/** 弹框头部统一高度，保证标题与各个按钮垂直居中对齐 */
private val DialogHeaderHeight = 44.dp

/**
 * 添加设备的悬浮弹框（磨砂玻璃）。
 *
 * 头部：左「取消」，中间标题「新增设备」，右侧依次为「相机」「相册」「确认」；
 * 点「相机」调起相机拍照，点「相册」调起相册选图（图片非必填）；
 * 下方是图片预览（可选）与设备昵称 / 编号 / 型号三个输入框。
 *
 * @param onConfirm 回调：昵称、编号、型号、图片 Uri（可为 null）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDeviceDialog(
    backdrop: Backdrop,
    inPreview: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (nickname: String, number: String, model: String, imageUri: String?) -> Unit,
) {
    val context = LocalContext.current
    var nickname by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }

    // 相册：系统图片选择器，无需存储权限
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) imageUri = uri.toString()
    }

    // 相机：拍照写入本应用 FileProvider 提供的 Uri
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingPhotoUri?.let { imageUri = it.toString() }
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "device_images").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        pendingPhotoUri = uri
        runCatching { cameraLauncher.launch(uri) }
    }

    val bitmap = rememberImageBitmap(imageUri)
    val canSave = nickname.isNotBlank()

    // 抓取输入法高度：记录出现过的最大值并跨弹窗保留，作为底边的固定预留
    val imeNow = WindowInsets.imeAnimationTarget.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(imeNow) {
        if (imeNow > RememberedImeHeight) RememberedImeHeight = imeNow
    }
    // 上下留白保持一致：底部离输入法、顶部离主顶栏下沿用同一个 gap
    val gap = 16.dp
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 主顶栏下沿 ≈ 状态栏 + 10dp 上边距 + 64dp 标题胶囊高，再留一个 gap
    val topLimit = statusTop + 74.dp + gap
    val density = LocalDensity.current

    // 入场：挂载后翻转 shown，面板从屏幕底部外平滑弹入、遮罩同步淡入
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    // 关闭：先把面板收回屏幕底部外、遮罩淡出，动画结束后再真正移除
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun requestClose() {
        if (closing) return
        closing = true
        shown = false
        scope.launch {
            delay(300)
            onDismiss()
        }
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "addDialogScrim"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DialogScrim.copy(alpha = DialogScrim.alpha * scrimAlpha)),
        contentAlignment = Alignment.Center
    ) {
        val screenH = maxHeight
        // 预留输入法高度做平滑过渡，避免首次抓取到底边跳变
        val reserved by animateDpAsState(
            targetValue = RememberedImeHeight,
            animationSpec = tween(durationMillis = 200),
            label = "addDialogImeReserve"
        )
        // 弹窗底边固定位置：整屏 - 预留输入法高度 - gap；无论输入法是否弹出都不被遮挡
        val bottomLine = screenH - reserved - gap
        // 弹窗最大高度：底边 - 顶部限制；内容超出时内部上下滚动
        val maxDialogHeight = (bottomLine - topLimit).coerceAtLeast(120.dp)
        // 在「顶栏下沿 ~ 键盘上沿」这段可用区域内垂直居中：
        // 无键盘时即整屏（顶栏以下）居中，有键盘时随预留高度整体上移，始终不被遮挡。
        // 不再依赖首次抓取到的输入法高度，避免第一次弹出时贴在屏幕底部
        val pinOffset = (topLimit - reserved) / 2
        val pinOffsetPx = with(density) { pinOffset.toPx() }
        // 入场上移量：初始在屏幕下方一整屏，shown 后回到 0
        val enter by animateDpAsState(
            targetValue = if (shown) 0.dp else screenH,
            animationSpec = tween(durationMillis = 280),
            label = "addDialogEnter"
        )
        val enterPx = with(density) { enter.toPx() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .heightIn(max = maxDialogHeight)
                // 在顶栏与键盘之间的可用区域垂直居中（不随输入法弹出再做避让）；
                // 叠加入场位移：刚弹入时从屏幕底部外滑上来
                .graphicsLayer { translationY = enterPx + pinOffsetPx }
                .frostedSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(28.dp),
                    inPreview = inPreview
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 头部固定：不随内容滚动
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DialogHeaderHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassIconButton(
                    backdrop = backdrop,
                    icon = MiuixIcons.Regular.Close,
                    contentDescription = "取消添加",
                    onClick = { requestClose() },
                    tint = Color(0xFFFF453A),
                    frosted = true
                )
                Text(
                    text = "新增设备",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                GlassTextButton(
                    backdrop = backdrop,
                    text = "相机",
                    onClick = { launchCamera() },
                    frosted = true
                )
                GlassTextButton(
                    backdrop = backdrop,
                    text = "相册",
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    frosted = true
                )
                GlassIconButton(
                    backdrop = backdrop,
                    icon = MiuixIcons.Regular.Ok,
                    contentDescription = "确认添加",
                    onClick = {
                        if (canSave) {
                            onConfirm(
                                nickname.trim(),
                                number.trim(),
                                model.trim(),
                                imageUri
                            )
                        }
                    },
                    tint = if (canSave) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                    frosted = true
                )
            }

            // 内容区：单独上下滚动，头部保持固定
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 图片预览（选了图才显示）
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "设备图片预览",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                DeviceField(hint = "设备昵称", value = nickname, onValueChange = { nickname = it })
                DeviceField(
                    hint = "设备编号",
                    value = number,
                    onValueChange = { number = it },
                    keyboardType = KeyboardType.Ascii
                )
                DeviceField(hint = "设备型号", value = model, onValueChange = { model = it })
            }
        }
    }
}

/**
 * 地图编辑弹框（磨砂玻璃）：可修改当前地图的名字与大小、切换地图、新建地图。
 *
 * 头部：最左「新建地图」(MapAlbum)、中间标题「地图编辑」、最右「保存」(对号)。
 * 所有改动先作用于本地草稿，点保存才写回 store；
 * 系统返回键或点击弹框外任意处直接关闭且不保存。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapEditDialog(
    backdrop: Backdrop,
    inPreview: Boolean,
    store: DeviceStore,
    onDismiss: () -> Unit,
) {
    // 草稿副本：编辑/新建/切换都只改它，保存时才整体写回 store
    val mapsDraft = remember { store.maps.toList().toMutableStateList() }
    var selectedDraftId by remember { mutableStateOf(store.selectedMapId) }
    val currentIndex = mapsDraft.indexOfFirst { it.id == selectedDraftId }
        .let { if (it < 0) 0 else it }
    val current = mapsDraft[currentIndex]

    // 入场：挂载后翻转 shown，面板从屏幕底部外平滑弹入、遮罩同步淡入
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    // 关闭：先把面板收回屏幕底部外、遮罩淡出，动画结束后再真正移除
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun requestClose() {
        if (closing) return
        closing = true
        shown = false
        scope.launch {
            delay(300)
            onDismiss()
        }
    }
    // 系统返回键：关闭且不保存
    BackHandler { requestClose() }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "mapEditScrim"
    )
    val density = LocalDensity.current

    // 抓取输入法高度：与新增设备弹窗共用同一份预留，保证底边任何时候不被输入法遮挡
    val imeNow = WindowInsets.imeAnimationTarget.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(imeNow) {
        if (imeNow > RememberedImeHeight) RememberedImeHeight = imeNow
    }
    // 上下留白保持一致：底部离输入法、顶部离主顶栏下沿用同一个 gap
    val gap = 16.dp
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 主顶栏下沿 ≈ 状态栏 + 10dp 上边距 + 64dp 标题胶囊高，再留一个 gap
    val topLimit = statusTop + 74.dp + gap

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DialogScrim.copy(alpha = DialogScrim.alpha * scrimAlpha))
            // 点击弹框外任意处：关闭且不保存
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { requestClose() },
        contentAlignment = Alignment.Center
    ) {
        val screenH = maxHeight
        // 预留输入法高度做平滑过渡，避免首次抓取到底边跳变
        val reserved by animateDpAsState(
            targetValue = RememberedImeHeight,
            animationSpec = tween(durationMillis = 200),
            label = "mapEditImeReserve"
        )
        // 弹窗底边固定位置：整屏 - 预留输入法高度 - gap；无论输入法是否弹出都不被遮挡
        val bottomLine = screenH - reserved - gap
        // 弹窗最大高度：底边 - 顶部限制；内容超出时内部上下滚动
        val maxDialogHeight = (bottomLine - topLimit).coerceAtLeast(120.dp)
        // 在「顶栏下沿 ~ 键盘上沿」这段可用区域内垂直居中：
        // 无键盘时即整屏（顶栏以下）居中，有键盘时随预留高度整体上移，始终不被遮挡。
        // 不再依赖首次抓取到的输入法高度，避免第一次弹出时贴在屏幕底部
        val pinOffset = (topLimit - reserved) / 2
        val pinOffsetPx = with(density) { pinOffset.toPx() }
        // 入场上移量：初始在屏幕下方一整屏，shown 后回到 0
        val enter by animateDpAsState(
            targetValue = if (shown) 0.dp else screenH,
            animationSpec = tween(durationMillis = 280),
            label = "mapEditEnter"
        )
        val enterPx = with(density) { enter.toPx() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .heightIn(max = maxDialogHeight)
                // 在顶栏与键盘之间的可用区域垂直居中（不随输入法弹出再做避让）；
                // 叠加入场位移：刚弹入时从屏幕底部外滑上来
                .graphicsLayer { translationY = enterPx + pinOffsetPx }
                .frostedSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(28.dp),
                    inPreview = inPreview
                )
                .padding(18.dp)
                // 消费弹框内部的点击，避免冒泡到遮罩误触关闭
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 头部：新建地图 | 删除 | 地图编辑(强制居中) | 保存
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DialogHeaderHeight)
            ) {
                // 标题强制居中：用整宽 Box 居中对齐，不受两侧按钮宽度影响
                Text(
                    text = "地图编辑",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center)
                )
                // 左侧两个按钮：拉开间距，避免贴在一起
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    GlassIconButton(
                        backdrop = backdrop,
                        icon = MiuixIcons.Regular.MapAlbum,
                        contentDescription = "新建地图",
                        onClick = {
                            val id = DeviceStore.newId()
                            mapsDraft.add(
                                DeviceMap(
                                    id = id,
                                    name = "地图 ${mapsDraft.size + 1}",
                                    rows = 4,
                                    cols = 4
                                )
                            )
                            selectedDraftId = id
                        },
                        size = 38.dp,
                        frosted = true
                    )
                    GlassIconButton(
                        backdrop = backdrop,
                        icon = MiuixIcons.Regular.Delete,
                        contentDescription = "删除当前地图",
                        onClick = {
                            // 至少保留一张地图；删除后选中相邻一张
                            if (mapsDraft.size > 1) {
                                val idx = currentIndex
                                mapsDraft.removeAt(idx)
                                selectedDraftId = mapsDraft[minOf(idx, mapsDraft.lastIndex)].id
                            }
                        },
                        size = 38.dp,
                        tint = Color(0xFFFF453A),
                        frosted = true
                    )
                }
                GlassIconButton(
                    backdrop = backdrop,
                    icon = MiuixIcons.Regular.Ok,
                    contentDescription = "保存",
                    onClick = {
                        store.replaceMaps(mapsDraft.toList(), selectedDraftId)
                        requestClose()
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    size = 38.dp,
                    frosted = true
                )
            }

            DeviceField(
                hint = "地图名称",
                value = current.name,
                onValueChange = { mapsDraft[currentIndex] = current.copy(name = it) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Stepper(
                    label = "行数",
                    value = current.rows,
                    min = 2,
                    max = 8,
                    onChange = { mapsDraft[currentIndex] = current.copy(rows = it) }
                )
                Stepper(
                    label = "列数",
                    value = current.cols,
                    min = 2,
                    max = 8,
                    onChange = { mapsDraft[currentIndex] = current.copy(cols = it) }
                )
            }

            Text(
                text = "全部地图",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            // 地图列表单独滚动：名称栏与行列栏保持固定不随列表滑动
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                mapsDraft.forEach { m ->
                    val selected = m.id == selectedDraftId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedDraftId = m.id }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = m.name,
                            style = MiuixTheme.textStyles.body1,
                            color = if (selected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${m.rows}×${m.cols}",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (selected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Image(
                                imageVector = MiuixIcons.Regular.Ok,
                                contentDescription = "当前地图",
                                modifier = Modifier.size(18.dp),
                                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 带文字的小玻璃按钮（用于弹框内的次要操作），高度与头部按钮对齐 */
@Composable
private fun GlassTextButton(
    backdrop: Backdrop,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    frosted: Boolean = false,
) {
    GlassButton(
        backdrop = backdrop,
        onClick = onClick,
        modifier = modifier.height(DialogHeaderHeight),
        frosted = frosted
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }
}

/** 数字步进器：- 值 +，用于调整地图行列数。图标不带圆形外框，直接显示 */
@Composable
private fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        StepIcon(
            icon = null,
            contentDescription = "减少$label",
            enabled = value > min,
            onClick = { onChange(value - 1) }
        )
        Text(
            text = value.toString(),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        StepIcon(
            icon = MiuixIcons.Regular.Add,
            contentDescription = "增加$label",
            enabled = value < max,
            onClick = { onChange(value + 1) }
        )
    }
}

/** 步进器的单个加/减图标：无外框，只有图标本身与点击区。icon 为 null 时绘制一根减号横条 */
@Composable
private fun StepIcon(
    icon: ImageVector?,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(tint)
            )
        } else {
            // 减号：miuix 没有纯减号图标（Remove 是门形），用一根圆角横条绘制
            Box(
                modifier = Modifier
                    .size(18.dp, 2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
        }
    }
}
