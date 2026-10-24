package com.example.hctool.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hctool.BuildConfig
import com.example.hctool.R
import com.example.hctool.ui.components.AppBackgroundImage
import com.example.hctool.ui.components.glassSurface
import com.example.hctool.ui.device.CardDivider
import com.example.hctool.ui.device.DeviceField
import com.example.hctool.ui.device.GlassIconButton
import com.example.hctool.ui.device.InfoRow
import com.example.hctool.ui.device.StaticCard
import com.example.hctool.ui.device.pressable
import com.example.hctool.ui.sync.SyncManager
import com.example.hctool.ui.theme.AppSettings
import com.example.hctool.ui.theme.LocalAppSettings
import com.example.hctool.ui.theme.ThemeMode
import com.example.hctool.ui.theme.label
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
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 关于卡片里展示的软件信息常量（作者与仓库地址按实际填写 / 替换）。 */
private const val APP_NAME = "HCtool"
private const val AUTHOR = "柒柒喵呜"
private const val GITHUB_URL = "https://github.com/RANqiqi7/HCtool"

/**
 * 设置页：设备页顶栏齿轮按钮打开，整屏从右侧滑入、盖住主页面。
 *
 * 四张磨砂卡片自上而下：
 * - 关于：应用图标 + 名称 + 版本号 + 构建日期 + 作者 + GitHub 链接（点击用浏览器打开）；
 * - 背景：当前背景缩略图 + 「更换背景」（系统相册选图，持久化 Uri）/「恢复默认」；
 * - 主题：跟随系统 / 浅色 / 深色 三档单选，选中项右侧打勾。
 * - 云同步：同步开关 + 服务器地址 / 访问令牌输入 + 立即同步按钮 + 状态行。
 *
 * 主题与背景都写进 [LocalAppSettings]，改完整站即时生效并落盘，重启保留。
 * 与设备详情同理：浮层自绘背景图、置于 imageBackdrop 之外采样，避免自采样崩溃。
 */
@Composable
fun SettingsScreen(
    backdrop: Backdrop,
    inPreview: Boolean,
    syncManager: SyncManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
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
    BackHandler { requestBack() }

    val settings = LocalAppSettings.current
    val context = LocalContext.current

    // 相册选图：OpenDocument 授予可持久化读权限，重启后仍能解码该 Uri 作为背景
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            settings.setBackgroundUri(uri.toString())
        }
    }

    // 悬浮顶栏采样「下方内容 + 背景图」，顶栏自身在该层之外
    val contentBackdrop = rememberLayerBackdrop { drawContent() }
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, contentBackdrop)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenW = maxWidth
        val slide by animateDpAsState(
            targetValue = if (shown) 0.dp else screenW,
            animationSpec = tween(durationMillis = 300),
            label = "settingsSlide"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = slide)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            AppBackgroundImage(modifier = Modifier.fillMaxSize(), inPreview = inPreview)

            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop)
                    .verticalScroll(rememberScrollState())
                    .padding(top = statusTop + 12.dp + 56.dp + 16.dp)
                    .padding(bottom = navBottom + 24.dp)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AboutCard(
                    backdrop = backdrop,
                    inPreview = inPreview,
                    onOpenGitHub = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                            )
                        }
                    }
                )
                BackgroundCard(
                    backdrop = backdrop,
                    inPreview = inPreview,
                    hasCustom = !settings.backgroundUri.isNullOrBlank(),
                    onPick = { pickImage.launch(arrayOf("image/*")) },
                    onReset = { settings.setBackgroundUri(null) }
                )
                ThemeCard(
                    backdrop = backdrop,
                    inPreview = inPreview,
                    current = settings.themeMode,
                    onPick = { settings.setThemeMode(it) }
                )
                SyncCard(
                    backdrop = backdrop,
                    inPreview = inPreview,
                    settings = settings,
                    syncManager = syncManager
                )
            }

            SettingsTopBar(
                backdrop = combinedBackdrop,
                inPreview = inPreview,
                onBack = { requestBack() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = statusTop + 12.dp)
                    .padding(horizontal = 20.dp)
            )
        }
    }
}

/** 设置页悬浮顶栏：左返回玻璃按钮 + 居中「设置」标题胶囊。 */
@Composable
private fun SettingsTopBar(
    backdrop: Backdrop,
    inPreview: Boolean,
    onBack: () -> Unit,
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
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pressable(onClick = { })
                .glassSurface(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(28.dp),
                    inPreview = inPreview
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "设置",
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
        // 右侧占位，使标题胶囊与左侧返回按钮左右对称
        Spacer(modifier = Modifier.size(56.dp))
    }
}

/** 关于卡片：图标 + 名称 + 版本 / 构建日期 + 作者 + GitHub 链接行。 */
@Composable
private fun AboutCard(
    backdrop: Backdrop,
    inPreview: Boolean,
    onOpenGitHub: () -> Unit,
) {
    StaticCard(backdrop = backdrop, inPreview = inPreview) {
        Text(
            text = "关于",
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 应用图标：启动器前景矢量图放在圆角底色块上，深浅色都协调
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = APP_NAME,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = APP_NAME,
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "版本 ${BuildConfig.VERSION_NAME}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        CardDivider()
        Spacer(modifier = Modifier.height(12.dp))
        InfoRow("构建日期", BuildConfig.BUILD_DATE)
        Spacer(modifier = Modifier.height(10.dp))
        InfoRow("作者", AUTHOR)
        Spacer(modifier = Modifier.height(10.dp))
        // GitHub 链接行：整行可点，右侧箭头提示可跳转
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenGitHub
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GitHub",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = GITHUB_URL.removePrefix("https://"),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Image(
                imageVector = MiuixIcons.Regular.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            )
        }
    }
}

/** 背景卡片：当前背景缩略图 + 更换 / 恢复默认。 */
@Composable
private fun BackgroundCard(
    backdrop: Backdrop,
    inPreview: Boolean,
    hasCustom: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    StaticCard(backdrop = backdrop, inPreview = inPreview) {
        Text(
            text = "软件背景",
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 当前背景缩略图
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                AppBackgroundImage(
                    modifier = Modifier.fillMaxSize(),
                    inPreview = inPreview
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingButton(
                    backdrop = backdrop,
                    inPreview = inPreview,
                    text = "更换背景",
                    onClick = onPick
                )
                if (hasCustom) {
                    SettingButton(
                        backdrop = backdrop,
                        inPreview = inPreview,
                        text = "恢复默认",
                        onClick = onReset
                    )
                }
            }
        }
    }
}

/** 主题卡片：跟随系统 / 浅色 / 深色 三档单选。 */
@Composable
private fun ThemeCard(
    backdrop: Backdrop,
    inPreview: Boolean,
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
) {
    StaticCard(backdrop = backdrop, inPreview = inPreview) {
        Text(
            text = "主题模式",
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        ThemeMode.entries.forEach { mode ->
            ThemeRow(
                label = mode.label,
                selected = mode == current,
                onClick = { onPick(mode) }
            )
        }
    }
}

/** 主题单选行：左侧名称，选中时右侧显示对号并高亮。 */
@Composable
private fun ThemeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = if (selected) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Image(
                imageVector = MiuixIcons.Regular.Ok,
                contentDescription = "已选",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary)
            )
        }
    }
}

/** 设置页里的通用文字按钮（磨砂胶囊，与全局玻璃同一材质体系）。 */
@Composable
private fun SettingButton(
    backdrop: Backdrop,
    inPreview: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

/** 云同步卡片：开关 + 服务器地址 / 令牌 + 立即同步 + 状态行。 */
@Composable
private fun SyncCard(
    backdrop: Backdrop,
    inPreview: Boolean,
    settings: AppSettings,
    syncManager: SyncManager,
) {
    val scope = rememberCoroutineScope()
    StaticCard(backdrop = backdrop, inPreview = inPreview) {
        Text(
            text = "云同步",
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(12.dp))
        // 开关行：启用后启动自动拉取、改动约 2 秒后自动推送
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "启用同步",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "启动自动拉取，改动后自动推送",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            MiniSwitch(checked = settings.syncEnabled) { settings.setSyncEnabled(it) }
        }
        Spacer(modifier = Modifier.height(12.dp))
        DeviceField(
            hint = "服务器地址，如 http://100.x.y.z:8080",
            value = settings.serverUrl,
            onValueChange = { settings.setServerUrl(it) },
            keyboardType = KeyboardType.Uri
        )
        Spacer(modifier = Modifier.height(8.dp))
        DeviceField(
            hint = "访问令牌（服务器 config.json 里的 token）",
            value = settings.serverToken,
            onValueChange = { settings.setServerToken(it) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingButton(
            backdrop = backdrop,
            inPreview = inPreview,
            text = if (syncManager.isSyncing) "同步中…" else "立即同步",
            onClick = {
                if (!syncManager.isSyncing) scope.launch { syncManager.syncNow() }
            }
        )
        // 状态行：上次同步时间与结果（成功条数 / 失败原因由 SyncManager 写入）
        if (settings.lastSyncResult.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = settings.lastSyncResult,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/** 小号滑动开关（与全局磨砂风配套）：开=主色底，关=半透灰底，白圆点左右滑动。 */
@Composable
private fun MiniSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // 圆点左边距比例：0f = 关，1f = 开
    val fraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "miniSwitch"
    )
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(
                if (checked) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.20f)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = 3.dp)
                .offset(x = (20.dp) * fraction)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
