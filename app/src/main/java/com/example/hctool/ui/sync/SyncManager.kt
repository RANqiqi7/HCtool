package com.example.hctool.ui.sync

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.hctool.ui.data.AppSnapshot
import com.example.hctool.ui.data.ImageStore
import com.example.hctool.ui.data.LocalDataStore
import com.example.hctool.ui.data.toDeviceSeed
import com.example.hctool.ui.device.DeviceStore
import com.example.hctool.ui.order.OrderStore
import com.example.hctool.ui.theme.AppSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 同步调度中心：把本地 [AppSnapshot] 与服务器整库文档做「拉平 + 推平」。
 *
 * - 配置来自 [AppSettings]（开关 / 地址 / 令牌），结果写回 lastSyncResult 供设置页展示。
 * - 状态记账存在自有 SharedPreferences（hctool_sync）：
 *   lastRev = 最后一次与服务器对齐的版本号；
 *   lastPushedJson = 最后一次确认「本地与服务器一致」的文档文本（脏检查基准，也防止拉完又推回去的死循环）。
 * - 冲突（本地有改动 && 服务器也有别人的改动）不自动合并：
 *   缓存服务器文档并置 [conflictPending]，由 UI 弹窗让用户裁决。
 * - 照片按文件名对账（imageUri 的 lastPathSegment）：本地有、服务器没有 → 上传；
 *   快照引用但本地文件缺失 → 从服务器下载到 filesDir/device_images/。
 *
 * 所有网络调用都在 IO 线程；同一时刻只跑一个同步（重入请求直接跳过）。
 */
@Stable
class SyncManager(
    context: Context,
    private val settings: AppSettings,
    private val deviceStore: DeviceStore,
    private val orderStore: OrderStore,
) {
    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences("hctool_sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /** 正在同步（UI 可据此禁用按钮） */
    var isSyncing by mutableStateOf(false)
        private set

    /** 有冲突等待用户在弹窗里裁决 */
    var conflictPending by mutableStateOf(false)
        private set

    private var pendingServerJson = ""
    private var pendingServerRev = -1

    /** 手动/自动同步入口：条件不满足时静默返回，异常全部转化为状态行文案 */
    suspend fun syncNow() {
        if (!settings.syncEnabled || settings.serverUrl.isBlank()) return
        if (conflictPending) return // 冲突没裁决前不再自动同步，避免反复覆盖
        if (!mutex.tryLock()) return // 已有一份在跑，跳过本次
        try {
            isSyncing = true
            runSync()
        } catch (e: Exception) {
            report("同步失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            isSyncing = false
            mutex.unlock()
        }
    }

    /** 冲突弹窗「用服务器数据覆盖本机 / 把本机数据强推上云」二选一 */
    suspend fun resolveConflict(useServer: Boolean) = withContext(Dispatchers.IO) {
        if (!conflictPending) return@withContext
        val client = newClient()
        try {
            if (useServer) {
                applyServerDocument(client, pendingServerRev, pendingServerJson)
                report("已用服务器数据覆盖本机")
            } else {
                val localJson = currentLocalJson()
                when (val result = client.pushState(pendingServerRev, localJson)) {
                    is SyncClient.PushResult.Success -> {
                        markPushed(result.rev, localJson)
                        syncPhotos(client, localJson)
                        report("已把本机数据强推上云（rev=${result.rev}）")
                    }
                    is SyncClient.PushResult.Conflict ->
                        report("强推失败：服务器又有了新改动，请重新同步后再试")
                }
            }
        } catch (e: Exception) {
            report("冲突处理失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            conflictPending = false
            pendingServerJson = ""
            pendingServerRev = -1
        }
    }

    /* ---------------- 核心流程 ---------------- */

    private suspend fun runSync() = withContext(Dispatchers.IO) {
        val client = newClient()
        val state = client.fetchState()
        val localJson = currentLocalJson()
        val lastPushed = sp.getString(KEY_LAST_PUSHED, "") ?: ""
        val serverNew = state.data.isNotEmpty() && state.data != lastPushed
        val localDirty = localJson != lastPushed

        when {
            serverNew && localDirty -> {
                pendingServerJson = state.data
                pendingServerRev = state.rev
                conflictPending = true
                report("数据冲突：本机与服务器都有改动，请选择保留哪边")
            }
            serverNew -> {
                applyServerDocument(client, state.rev, state.data)
                report("已从服务器拉取（rev=${state.rev}）")
            }
            localDirty -> pushLocal(client, state.rev, localJson)
            else -> {
                // 数据已一致：只补齐两边缺的照片
                syncPhotos(client, localJson)
                report("同步完成，已是最新（rev=${state.rev}）")
            }
        }
    }

    /** 推送本地改动；409 时重取服务器状态：有别人改动 → 冲突弹窗，没内容变化 → 带最新 rev 重试一次 */
    private suspend fun pushLocal(client: SyncClient, baseRev: Int, localJson: String) {
        var rev = baseRev
        var retried = false
        while (true) {
            when (val result = client.pushState(rev, localJson)) {
                is SyncClient.PushResult.Success -> {
                    markPushed(result.rev, localJson)
                    syncPhotos(client, localJson)
                    report("已推送到服务器（rev=${result.rev}）")
                    return
                }
                is SyncClient.PushResult.Conflict -> {
                    val fresh = client.fetchState()
                    val lastPushed = sp.getString(KEY_LAST_PUSHED, "") ?: ""
                    val serverActuallyNew = fresh.data.isNotEmpty() && fresh.data != lastPushed
                    if (serverActuallyNew || retried) {
                        pendingServerJson = fresh.data
                        pendingServerRev = fresh.rev
                        conflictPending = true
                        report("数据冲突：本机与服务器都有改动，请选择保留哪边")
                        return
                    }
                    retried = true
                    rev = fresh.rev
                }
            }
        }
    }

    /** 把服务器文档落到界面 + 磁盘，并记下对齐状态（防止拉完又被当成脏数据推回去） */
    private suspend fun applyServerDocument(client: SyncClient, rev: Int, data: String) {
        val snapshot = LocalDataStore.decode(data)
        if (snapshot == null) {
            report("同步失败：服务器数据格式无法识别")
            return
        }
        val seed = snapshot.toDeviceSeed()
        withContext(Dispatchers.Main) {
            deviceStore.maps.clear()
            deviceStore.maps.addAll(seed.maps)
            deviceStore.devices.clear()
            deviceStore.devices.addAll(seed.devices)
            deviceStore.selectedMapId = seed.selectedMapId
            orderStore.orders.clear()
            orderStore.orders.addAll(snapshot.orders)
        }
        LocalDataStore.saveJson(appContext, data)
        markPushed(rev, data)
        syncPhotos(client, data)
    }

    /* ---------------- 照片对账 ---------------- */

    private fun syncPhotos(client: SyncClient, dataJson: String) {
        val snapshot = LocalDataStore.decode(dataJson) ?: return
        val names = photoNames(snapshot)
        if (names.isEmpty()) return
        val remote = runCatching { client.listPhotos().toSet() }.getOrNull() ?: return
        val imagesDir = File(appContext.filesDir, ImageStore.DIR).apply { mkdirs() }
        for (name in names) {
            val local = localPhotoFile(name)
            if (local == null) {
                // 本地缺、服务器有 → 下载补齐
                if (name in remote) runCatching { client.downloadPhoto(name, File(imagesDir, name)) }
            } else if (name !in remote) {
                // 本地有、服务器缺 → 上传，成功后留一份到 filesDir 防缓存被清
                runCatching {
                    client.uploadPhoto(name, local)
                    if (local.parentFile != imagesDir) local.copyTo(File(imagesDir, name), overwrite = true)
                }
            }
        }
    }

    /** 照片文件先找 filesDir（下载来的），再找 cacheDir（拍照生成的原始位置） */
    private fun localPhotoFile(name: String): File? {
        val inFiles = File(appContext.filesDir, "${ImageStore.DIR}/$name")
        if (inFiles.isFile) return inFiles
        val inCache = File(appContext.cacheDir, "${ImageStore.DIR}/$name")
        return if (inCache.isFile) inCache else null
    }

    private fun photoNames(snapshot: AppSnapshot): List<String> =
        (snapshot.devices.mapNotNull { it.imageUri } + snapshot.orders.mapNotNull { it.imageUri })
            .mapNotNull { uri -> runCatching { Uri.parse(uri).lastPathSegment }.getOrNull() }
            .filter { it.isNotEmpty() }
            .distinct()

    /* ---------------- 小工具 ---------------- */

    private fun newClient() = SyncClient(settings.serverUrl, settings.serverToken)

    private fun currentLocalJson(): String =
        LocalDataStore.encode(LocalDataStore.snapshotOf(deviceStore, orderStore))

    private fun markPushed(rev: Int, data: String) {
        sp.edit().putInt(KEY_LAST_REV, rev).putString(KEY_LAST_PUSHED, data).apply()
    }

    private fun report(text: String) {
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())
        settings.setLastSyncResult("$time · $text")
    }

    private companion object {
        const val KEY_LAST_REV = "lastRev"
        const val KEY_LAST_PUSHED = "lastPushedJson"
    }
}

/** 在 MainScreen 创建唯一实例，随两个 store 存活 */
@Composable
fun rememberSyncManager(
    settings: AppSettings,
    deviceStore: DeviceStore,
    orderStore: OrderStore,
): SyncManager {
    val context = LocalContext.current
    return remember { SyncManager(context, settings, deviceStore, orderStore) }
}
