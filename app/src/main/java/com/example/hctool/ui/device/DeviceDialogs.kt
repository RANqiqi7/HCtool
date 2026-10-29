package com.example.hctool.ui.device

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.hctool.ui.components.dialogSurface
import com.example.hctool.ui.data.ImageStore
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 弹框背后的半透明遮罩：加深一档，让弹窗从背景中更突出 */
private val DialogScrim = Color.Black.copy(alpha = 0.55f)

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
    // 关闭动画与选图落地都要用，声明提前以便下面的 launcher 回调复用
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }

    // 相册：系统图片选择器，无需存储权限；
    // 选择器给的 Uri 是一次性临时授权，必须当场拷成自家永久副本，否则重启就读不出来
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) scope.launch {
            imageUri = ImageStore.persistPicked(context, uri) ?: uri.toString()
        }
    }

    // 相机：拍照直接落在永久目录，再经 FileProvider 授权给相机 App 写入
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingPhotoUri?.let { imageUri = it.toString() }
    }

    fun launchCamera() {
        val uri = ImageStore.newCameraTarget(context)
        pendingPhotoUri = uri
        runCatching { cameraLauncher.launch(uri) }
    }

    val bitmap = rememberImageBitmap(imageUri)
    val canSave = nickname.isNotBlank()

    // 上下留白保持一致：底部离输入法/底栏、顶部离主顶栏下沿用同一个 gap
    val gap = 16.dp
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 输入法实时高度：底边跟随其上沿同步向上避让
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    // 主顶栏下沿 ≈ 状态栏 + 10dp 上边距 + 64dp 标题胶囊高，再留一个 gap；顶边额外下移 7dp
    val topLimit = statusTop + 81.dp + gap

    // 入场：挂载后翻转 shown，面板从屏幕底部外平滑弹入、遮罩同步淡入
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    // 关闭：先把面板收回屏幕底部外、遮罩淡出，动画结束后再真正移除
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
    // 系统返回键：只关闭本弹窗，不能泄到 Activity 默认行为把应用退掉
    BackHandler { requestClose() }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "addDialogScrim"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DialogScrim.copy(alpha = DialogScrim.alpha * scrimAlpha))
            // 弹窗存在时吃掉弹框外的点击，避免穿透响应下层设备组件；
            // 点击弹框外任意处：关闭弹窗（与地图编辑弹窗一致）
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { requestClose() },
        contentAlignment = Alignment.TopCenter
    ) {
        val screenH = maxHeight
        // 底边上限：无输入法时靠近悬浮底栏再隔一点距离；
        // 有输入法时跟随输入法上沿同步上移；取较小值保证切换连续不跳变
        val idleLimit = screenH - navBottom - 84.dp - gap
        val imeLimit = screenH - imeBottom - gap
        val bottomLimit = minOf(idleLimit, imeLimit)
        // 弹窗最大高度：顶边（顶栏下沿）~ 底边上限；内容超出时内部上下滚动
        val maxDialogHeight = (bottomLimit - topLimit).coerceAtLeast(120.dp)
        // 入场上移量：初始在屏幕下方一整屏，shown 后回到 0
        val enter by animateDpAsState(
            targetValue = if (shown) 0.dp else screenH,
            animationSpec = tween(durationMillis = 280),
            label = "addDialogEnter"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                // 顶边钉在顶栏下沿：内容增多时只向下拉长，不向上顶；
                // 叠加 enter 位移：刚弹入时整块（含磨砂边框）从屏幕底部之外滑上来。
                // 用布局 offset 而非 graphicsLayer，保证玻璃边框与内容一起移动
                .offset(y = topLimit + enter)
                .heightIn(max = maxDialogHeight)
                // 内容增减 / 输入法避让时高度平滑过渡，不突然跳变
                .animateContentSize()
                .dialogSurface(
                    backdrop = backdrop,
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
            // 头部固定：不随内容滚动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DialogHeaderHeight)
            ) {
                // 标题强制居中：用整宽 Box 居中对齐，不受两侧按钮宽度影响
                Text(
                    text = "新增设备",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center)
                )
                // 左侧：相机（Scan 图标）与相册（Photos 图标），均为纯图标按钮
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassIconButton(
                        backdrop = backdrop,
                        icon = MiuixIcons.Regular.Scan,
                        contentDescription = "相机",
                        onClick = { launchCamera() },
                        frosted = true
                    )
                    GlassIconButton(
                        backdrop = backdrop,
                        icon = MiuixIcons.Regular.Photos,
                        contentDescription = "相册",
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        frosted = true
                    )
                }
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
                            // 保存后同样从屏幕底部之外滑出再移除
                            requestClose()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
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

    // 上下留白保持一致：底部离输入法/底栏、顶部离主顶栏下沿用同一个 gap
    val gap = 16.dp
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 输入法实时高度：底边跟随其上沿同步向上避让
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    // 主顶栏下沿 ≈ 状态栏 + 10dp 上边距 + 64dp 标题胶囊高，再留一个 gap；顶边额外下移 7dp
    val topLimit = statusTop + 81.dp + gap

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DialogScrim.copy(alpha = DialogScrim.alpha * scrimAlpha))
            // 点击弹框外任意处：关闭且不保存
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { requestClose() },
        contentAlignment = Alignment.TopCenter
    ) {
        val screenH = maxHeight
        // 底边上限：无输入法时靠近悬浮底栏再隔一点距离；
        // 有输入法时跟随输入法上沿同步上移；取较小值保证切换连续不跳变
        val idleLimit = screenH - navBottom - 84.dp - gap
        val imeLimit = screenH - imeBottom - gap
        val bottomLimit = minOf(idleLimit, imeLimit)
        // 弹窗最大高度：顶边（顶栏下沿）~ 底边上限；内容超出时内部上下滚动
        val maxDialogHeight = (bottomLimit - topLimit).coerceAtLeast(120.dp)
        // 入场上移量：初始在屏幕下方一整屏，shown 后回到 0
        val enter by animateDpAsState(
            targetValue = if (shown) 0.dp else screenH,
            animationSpec = tween(durationMillis = 280),
            label = "mapEditEnter"
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                // 顶边钉在顶栏下沿：内容增多时只向下拉长，不向上顶；
                // 叠加 enter 位移：刚弹入时整块（含磨砂边框）从屏幕底部之外滑上来。
                // 用布局 offset 而非 graphicsLayer，保证玻璃边框与内容一起移动
                .offset(y = topLimit + enter)
                .heightIn(max = maxDialogHeight)
                // 内容增减 / 输入法避让时高度平滑过渡，不突然跳变
                .animateContentSize()
                .dialogSurface(
                    backdrop = backdrop,
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
                    max = 11,
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
            // 地图列表随地图数量向下拉长：弹窗顶到上限（靠近底栏）后才在列表内部滚动；
            // 名称栏与行列栏保持固定不随列表滑动
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
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
