package com.example.hctool.ui.device

import android.graphics.BitmapFactory
import android.net.Uri
import android.app.Activity
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.example.hctool.ui.components.GlassButton
import com.example.hctool.ui.components.frostedSurface
import com.example.hctool.ui.order.OrderStore
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
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
    StatusDot(
        color = if (status == DeviceStatus.IN_USE) StatusInUseColor else StatusIdleColor,
        modifier = modifier,
        size = size
    )
}

/**
 * 直接按颜色画的状态圆点（与设备状态圆点同款尺寸 / 形状）。
 * 订单进行中蓝 / 已完结绿等场景复用它，保证全站圆点观感一致。
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * 单张设备卡片（首页设备列表与订单详情页「关联设备」共用，保证两处观感完全一致）：
 * - 主标题 = 设备昵称，左侧一个状态圆点（空闲绿 / 使用中蓝）
 * - 副标题 = 从设备编号正则提取的序号
 * - 有图片则展示图片，无图片则不展示
 */
@Composable
fun DeviceCard(
    device: Device,
    orderStore: OrderStore,
    backdrop: Backdrop,
    inPreview: Boolean,
    onClick: (Device) -> Unit,
) {
    val bitmap = rememberImageBitmap(device.imageUri)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 按压反馈需在磨砂绘制之前，缩放才能带动玻璃背景一起收缩
            .pressable(onClick = { onClick(device) })
            .frostedSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(22.dp),
                inPreview = inPreview
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 运行状态由关联订单是否有测试中节点推导
            StatusDot(device.computeStatus(orderStore))
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
 * 详情页通用信息卡片：磨砂玻璃 + 点击轻微收缩 + 平滑伸缩（点击标题栏展开 / 收起）。
 * 标题栏右侧可放附加内容（[headerRight]，如「历史订单」、进度环），末尾是一个随展开旋转的箭头。
 * 设备详情页与订单详情页共用，保证两页卡片观感完全一致。
 *
 * @param expanded      是否展开（collapsible = false 时无意义，内容始终可见）
 * @param onToggle      点击整卡切换展开 / 收起
 * @param collapsible   是否允许展开 / 收起：false 时不画箭头、内容常驻完整显示
 * @param headerRight   标题与箭头之间的附加内容（可选）
 * @param previewContent 收起态也常驻的预览内容（不参与展开动画，如关联订单的第一条）
 * @param content       展开时显示的内容
 */
@Composable
fun DetailCard(
    backdrop: Backdrop,
    inPreview: Boolean,
    title: String,
    expanded: Boolean = true,
    onToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
    headerRight: (@Composable RowScope.() -> Unit)? = null,
    previewContent: (@Composable ColumnScope.() -> Unit)? = null,
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
            // 不可折叠的卡片不画箭头，也没有可点击的展开暗示
            if (collapsible) {
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
        }
        // 收起态也常驻的预览内容：不参与展开动画，保证收起时卡片不空
        previewContent?.let { preview ->
            Spacer(modifier = Modifier.height(12.dp))
            preview()
        }
        if (collapsible) {
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
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/** 详情页常驻磨砂卡片外框：只有按压反馈，不带箭头、不可展开（基础信息卡用它）。 */
@Composable
fun StaticCard(
    backdrop: Backdrop,
    inPreview: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = { })
            .clip(RoundedCornerShape(24.dp))
            .frostedSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(24.dp),
                inPreview = inPreview
            )
            .padding(16.dp),
        content = content
    )
}

/** 详情页一行「标签 + 取值」信息：标签居左淡显，取值紧随其后，过长省略。 */
@Composable
fun InfoRow(label: String, value: String) {
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

/** 详情页卡片内分隔线 */
@Composable
fun CardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    )
}

/** 详情页空数据占位提示 */
@Composable
fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}

/**
 * 调起软键盘（已显示则跳过）：部分机型 / edge-to-edge 下 Compose 输入框聚焦
 * 不会自动弹键盘，聚焦后用 InsetsController 主动显示（比 SoftwareKeyboardController 更可靠）。
 * 键盘已经显示时绝不重发 show 请求：重复 show 与外部 hide 竞争会把输入法
 * insets 动画 leash 卡死，之后全系统所有输入框都弹不起键盘。
 */
fun showIme(view: View, keyboard: SoftwareKeyboardController?) {
    if (ViewCompat.getRootWindowInsets(view)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    ) {
        return
    }
    val window = (view.context as? Activity)?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, view) }
    if (controller != null) {
        controller.show(WindowInsetsCompat.Type.ime())
    } else {
        keyboard?.show()
    }
}

/** 收起软键盘（未显示则跳过，理由同 showIme：无谓的 hide 请求同样会参与竞争） */
fun hideIme(view: View, keyboard: SoftwareKeyboardController?) {
    if (ViewCompat.getRootWindowInsets(view)
            ?.isVisible(WindowInsetsCompat.Type.ime()) != true
    ) {
        return
    }
    val window = (view.context as? Activity)?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, view) }
    if (controller != null) {
        controller.hide(WindowInsetsCompat.Type.ime())
    } else {
        keyboard?.hide()
    }
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
    // 聚焦后走带守卫的 showIme 主动显示（已显示则跳过，防 show/hide 竞争卡死输入法）
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    // 每次按下自增一下：用系统返回收起键盘后输入框仍持有焦点，focused 不会变化，
    // 只监听 focused 会导致「再点输入框弹不起键盘」
    var pressTick by remember { mutableStateOf(0) }
    LaunchedEffect(focused, pressTick) {
        if (!focused) return@LaunchedEffect
        // 等输入连接建立后再弹，避免过早调用被忽略
        delay(60)
        showIme(view, keyboard)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            // 只观察按下、不消费事件，不影响子级输入框定位光标
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressTick++
                }
            }
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

/**
 * 列表页副标题长条：磨砂圆角外框 + 标题居左 + 右侧纯图标筛选按钮。
 * 设备页与订单页共用，保证两条长条高度、内边距与筛选按钮完全一致。
 *
 * @param onPositionChanged 回报顶 / 底边在整页中的 y 坐标，供筛选列表定向与定位
 */
@Composable
fun ListHeader(
    backdrop: Backdrop,
    inPreview: Boolean,
    title: String,
    filterActive: Boolean,
    onToggleFilter: () -> Unit,
    onPositionChanged: (Dp, Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                val bounds = it.boundsInRoot()
                onPositionChanged(
                    with(density) { bounds.top.toDp() },
                    with(density) { bounds.bottom.toDp() }
                )
            }
            .frostedSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(22.dp),
                inPreview = inPreview
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        // 筛选按钮：纯图标，不带圆形按钮框
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleFilter
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                imageVector = MiuixIcons.Regular.Filter,
                contentDescription = "筛选",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    if (filterActive) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    }
                )
            )
        }
    }
}

/**
 * 筛选下拉面板：标签按组平铺，组间用分割线隔开，不做子级列表；
 * 选中项高亮并显示对号；高度超过 maxHeight 时内部上下滚动。
 * 设备页与订单页共用，用「组下标 + 项下标」定位选中项，避免标签文字跨组重复时歧义。
 *
 * @param groups    按组平铺的标签文字
 * @param active    当前选中的（组下标，项下标），null = 未筛选
 * @param maxHeight 可用最大高度（由调用方按上下底栏界限钳制）
 */
@Composable
fun FilterMenu(
    backdrop: Backdrop,
    inPreview: Boolean,
    groups: List<List<String>>,
    active: Pair<Int, Int>?,
    maxHeight: Dp,
    onPick: (group: Int, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(230.dp)
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(20.dp))
            .frostedSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(20.dp),
                inPreview = inPreview
            )
            .padding(vertical = 6.dp)
            .verticalScroll(rememberScrollState())
    ) {
        groups.forEachIndexed { gi, group ->
            // 不同筛选类型之间用分割线隔开
            if (gi > 0) MenuDivider()
            group.forEachIndexed { ii, label ->
                FilterRow(
                    text = label,
                    selected = active == gi to ii
                ) { onPick(gi, ii) }
            }
        }
    }
}

/** 筛选面板里的分割线 */
@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.15f))
    )
}

/** 筛选面板里的一行标签：选中时高亮并显示对号 */
@Composable
private fun FilterRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body1,
            color = if (selected) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Image(
                imageVector = MiuixIcons.Regular.Ok,
                contentDescription = "已选",
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary)
            )
        }
    }
}
