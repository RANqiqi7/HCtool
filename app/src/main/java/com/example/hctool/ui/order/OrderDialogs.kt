package com.example.hctool.ui.order

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
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.hctool.ui.components.dialogSurface
import com.example.hctool.ui.device.DeviceField
import com.example.hctool.ui.device.GlassIconButton
import com.example.hctool.ui.device.rememberImageBitmap
import com.kyant.backdrop.Backdrop
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 弹框背后的半透明遮罩：加深一档，让弹窗从背景中更突出 */
private val OrderDialogScrim = Color.Black.copy(alpha = 0.55f)

/** 弹框头部统一高度，保证标题与各个按钮垂直居中对齐 */
private val OrderDialogHeaderHeight = 44.dp

/**
 * 新增订单的悬浮弹框（磨砂玻璃），样式 / 弹出效果 / 开关逻辑 / 材质均照抄新增设备弹窗。
 *
 * 头部：左侧「相机」「相册」可添加订单图片，中间标题「新增订单」，右侧「保存」(对号)；
 * 下方是图片预览（可选）与七个输入项：订单编号、客户代称（必填），
 * 样品名称、样品数量、样品编号、测试条件、Case Owner（选填）。
 * 仅前两项必填，填不填其余项不影响保存。
 *
 * @param onConfirm 回调：订单编号、客户代称、样品名称、样品数量、样品编号、测试条件、Case Owner、图片 Uri（可为 null）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOrderDialog(
    backdrop: Backdrop,
    inPreview: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        orderNo: String,
        customer: String,
        sampleName: String,
        sampleCount: String,
        sampleNo: String,
        testCondition: String,
        caseOwner: String,
        imageUri: String?,
    ) -> Unit,
) {
    val context = LocalContext.current
    var orderNo by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var sampleName by remember { mutableStateOf("") }
    var sampleCount by remember { mutableStateOf("") }
    var sampleNo by remember { mutableStateOf("") }
    var testCondition by remember { mutableStateOf("") }
    var caseOwner by remember { mutableStateOf("") }
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
    // 仅订单编号与客户代称为必填项
    val canSave = orderNo.isNotBlank() && customer.isNotBlank()

    // 上下留白保持一致：底部离输入法/底栏、顶部离主顶栏下沿用同一个 gap
    val gap = 16.dp
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 输入法实时高度：底边跟随其上沿同步向上避让
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    // 主顶栏下沿 ≈ 状态栏 + 10dp 上边距 + 64dp 标题胶囊高，再留一个 gap
    val topLimit = statusTop + 74.dp + gap

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
        label = "addOrderScrim"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OrderDialogScrim.copy(alpha = OrderDialogScrim.alpha * scrimAlpha))
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
            label = "addOrderEnter"
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
                    .height(OrderDialogHeaderHeight)
            ) {
                // 标题强制居中：用整宽 Box 居中对齐，不受两侧按钮宽度影响
                Text(
                    text = "新增订单",
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
                // 右侧：保存按钮，必填项未填时置灰
                GlassIconButton(
                    backdrop = backdrop,
                    icon = MiuixIcons.Regular.Ok,
                    contentDescription = "保存订单",
                    onClick = {
                        if (canSave) {
                            onConfirm(
                                orderNo.trim(),
                                customer.trim(),
                                sampleName.trim(),
                                sampleCount.trim(),
                                sampleNo.trim(),
                                testCondition.trim(),
                                caseOwner.trim(),
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
                        contentDescription = "订单图片预览",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                // 必填项
                DeviceField(
                    hint = "订单编号（必填）",
                    value = orderNo,
                    onValueChange = { orderNo = it },
                    keyboardType = KeyboardType.Ascii
                )
                DeviceField(hint = "客户代称（必填）", value = customer, onValueChange = { customer = it })
                // 选填项
                DeviceField(hint = "样品名称", value = sampleName, onValueChange = { sampleName = it })
                DeviceField(
                    hint = "样品数量",
                    value = sampleCount,
                    onValueChange = { sampleCount = it },
                    keyboardType = KeyboardType.Number
                )
                DeviceField(hint = "样品编号", value = sampleNo, onValueChange = { sampleNo = it })
                DeviceField(hint = "测试条件", value = testCondition, onValueChange = { testCondition = it })
                DeviceField(hint = "Case Owner", value = caseOwner, onValueChange = { caseOwner = it })
            }
        }
    }
}
