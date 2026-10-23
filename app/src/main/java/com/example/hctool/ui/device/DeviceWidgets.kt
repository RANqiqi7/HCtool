package com.example.hctool.ui.device

import android.graphics.BitmapFactory
import android.net.Uri
import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.tooling.preview.Preview
import com.example.hctool.MainScreen
import com.example.hctool.ui.components.GlassButton
import com.example.hctool.ui.theme.HCtoolTheme
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一的圆形玻璃图标按钮（复用团队规范里的 GlassButton：同一套材质 + 按压反馈）。
 * 设备页里所有小图标按钮都用它，保证质感一致。
 */
@Composable
fun GlassIconButton(
    backdrop: Backdrop,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MiuixTheme.colorScheme.primary,
    frosted: Boolean = false,
) {
    GlassButton(
        backdrop = backdrop,
        onClick = onClick,
        modifier = modifier.size(size),
        frosted = frosted
    ) {
        Image(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.52f),
            colorFilter = ColorFilter.tint(tint)
        )
    }
}

/**
 * 设备运行状态圆点（与设备卡片上那个一致）：空闲绿 / 使用中蓝。
 * 详情页头部与列表卡片共用，保证颜色与尺寸一致。
 */
@Composable
fun StatusDot(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (status == DeviceStatus.IN_USE) StatusInUseColor else StatusIdleColor
            )
    )
}

/**
 * 统一按压反馈：按下轻微收缩、弹簧回弹（与全局玻璃按钮同一套手感）。
 * 供卡片 / 标题胶囊等大尺寸可点表面复用，保证所有按钮和卡片按压手感一致。
 *
 * @param onClick      点击回调
 * @param pressedScale 按下缩放比例，默认 0.98（大表面用较缓和的收缩）
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    pressedScale: Float = 0.98f,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "pressScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * 圆角输入栏（与顶部搜索框同款外观）：提示词作为 placeholder 显示在栏内，
 * 不计入实际输入内容。基于 BasicTextField，聚焦后主动调起软键盘。
 *
 * @param hint 栏内提示词（内容为空时显示）
 */
@Composable
fun DeviceField(
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    // 部分机型/edge-to-edge 下 Compose 输入框聚焦不会自动弹键盘：
    // 聚焦后用 InsetsController 主动显示 IME（比 SoftwareKeyboardController 更可靠）
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            // 等输入连接建立后再弹，避免过早调用被忽略；
            // 只发一次显示请求：重复 show 与外部 hide 竞争会把系统输入法 insets 动画卡死
            delay(60)
            val window = (view.context as? Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            if (controller != null) {
                controller.show(WindowInsetsCompat.Type.ime())
            } else {
                keyboard?.show()
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MiuixTheme.textStyles.body1.copy(
                color = MiuixTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * 按 Uri 字符串解码出降采样后的位图，供卡片/弹框预览使用。
 * 在 IO 线程解码，失败或为空返回 null。
 */
@Composable
fun rememberImageBitmap(uriString: String?, maxDimension: Int = 512): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = null, key1 = uriString) {
        value = if (uriString.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { decodeSampledBitmap(context, uriString, maxDimension) }.getOrNull()
            }
        }
    }
    return state.value
}

private fun decodeSampledBitmap(
    context: android.content.Context,
    uriString: String,
    maxDimension: Int,
): ImageBitmap? {
    val uri = Uri.parse(uriString)
    val resolver = context.contentResolver
    // 先只读尺寸，算出合适的采样率，避免解码大图 OOM
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxDimension * 2 ||
        bounds.outHeight / sample > maxDimension * 2
    ) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    } ?: return null
    return bitmap.asImageBitmap()
}
