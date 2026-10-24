package com.example.hctool.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 主题模式：跟随系统 / 浅色 / 深色。
 * id 即持久化到 SharedPreferences 的整数值，新增档位只往后加，别改旧值。
 */
enum class ThemeMode(val id: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromId(id: Int): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** 设置项的中文标题（跟随系统 / 浅色模式 / 深色模式），供设置页主题卡片平铺 */
val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色模式"
        ThemeMode.DARK -> "深色模式"
    }

/**
 * 应用级设置：主题模式与自定义背景图 Uri。
 *
 * 用 SharedPreferences 落盘，进程重启后自动恢复；内存里以 Compose 状态持有，
 * 任何一处 set 都会触发读取它的组件重组（换主题、换背景即时全站生效）。
 * 在 MainActivity 根节点创建一份，经 [LocalAppSettings] 向下提供。
 */
@Stable
class AppSettings(context: Context) {
    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences("hctool_settings", Context.MODE_PRIVATE)

    /** 当前主题模式（只读，改经 [setThemeMode]；私有幕后字段避免与 setter 函数 JVM 签名冲突） */
    private var _themeMode by mutableStateOf(ThemeMode.fromId(sp.getInt(KEY_THEME, ThemeMode.SYSTEM.id)))
    val themeMode: ThemeMode get() = _themeMode

    /** 自定义背景图的 content Uri 字符串；null / 空 = 用内置默认背景（只读，改经 [setBackgroundUri]） */
    private var _backgroundUri by mutableStateOf(sp.getString(KEY_BG, null))
    val backgroundUri: String? get() = _backgroundUri

    fun setThemeMode(mode: ThemeMode) {
        _themeMode = mode
        sp.edit().putInt(KEY_THEME, mode.id).apply()
    }

    /** 设置 / 清除（传 null）自定义背景；调用方负责先申请好持久化读权限 */
    fun setBackgroundUri(uriString: String?) {
        _backgroundUri = uriString
        sp.edit().putString(KEY_BG, uriString).apply()
    }

    /* ---- 云同步配置：开关 / 服务器地址 / 访问令牌 / 上次同步结果 ---- */

    private var _syncEnabled by mutableStateOf(sp.getBoolean(KEY_SYNC_ON, false))
    val syncEnabled: Boolean get() = _syncEnabled
    fun setSyncEnabled(on: Boolean) {
        _syncEnabled = on
        sp.edit().putBoolean(KEY_SYNC_ON, on).apply()
    }

    private var _serverUrl by mutableStateOf(sp.getString(KEY_SERVER_URL, "") ?: "")
    val serverUrl: String get() = _serverUrl
    fun setServerUrl(url: String) {
        _serverUrl = url.trim()
        sp.edit().putString(KEY_SERVER_URL, _serverUrl).apply()
    }

    private var _serverToken by mutableStateOf(sp.getString(KEY_SERVER_TOKEN, "") ?: "")
    val serverToken: String get() = _serverToken
    fun setServerToken(token: String) {
        _serverToken = token.trim()
        sp.edit().putString(KEY_SERVER_TOKEN, _serverToken).apply()
    }

    /** 上次同步结果的一行文案（时间 + 成功/失败原因），供设置页状态行展示 */
    private var _lastSyncResult by mutableStateOf(sp.getString(KEY_LAST_SYNC, "") ?: "")
    val lastSyncResult: String get() = _lastSyncResult
    fun setLastSyncResult(text: String) {
        _lastSyncResult = text
        sp.edit().putString(KEY_LAST_SYNC, text).apply()
    }

    private companion object {
        const val KEY_THEME = "themeMode"
        const val KEY_BG = "backgroundUri"
        const val KEY_SYNC_ON = "syncEnabled"
        const val KEY_SERVER_URL = "serverUrl"
        const val KEY_SERVER_TOKEN = "serverToken"
        const val KEY_LAST_SYNC = "lastSyncResult"
    }
}

/**
 * 向下传递唯一的 AppSettings 实例。HCtoolTheme、背景绘制、设置页都从这里读取，
 * 保证「一处修改、全站响应」。根节点未 provide 时访问会抛错。
 */
val LocalAppSettings = staticCompositionLocalOf<AppSettings> {
    error("AppSettings 未通过 CompositionLocalProvider 提供")
}

/** 在根节点创建并记忆一份 AppSettings（随进程存活，读取 SharedPreferences 一次）。 */
@Composable
fun rememberAppSettings(): AppSettings {
    val context = LocalContext.current
    return remember { AppSettings(context) }
}
