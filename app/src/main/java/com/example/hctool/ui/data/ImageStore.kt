package com.example.hctool.ui.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 备注图片的永久仓库：统一落在 filesDir/[DIR] 下，一张一个文件。
 *
 * 为什么必须自己拷一份，而不能直接存选图返回的 Uri：
 * - 相册选择器返回的 `content://…/picker/…` 只是**一次性临时读授权**，App 进程一死就作废，
 *   把它原样存进数据里，下次启动图片必然读不出来（表现为「图片凭空消失」）。
 * - 拍照文件若放 cacheDir，系统空间紧张或清理类 App 会随时清空缓存。
 *
 * 所以选图当场就把字节固化进应用私有的 filesDir，数据里只存这份永久副本的地址；
 * 顺带让云同步能按文件名找到实体文件去上传（SyncManager 认的就是这个目录）。
 */
object ImageStore {

    /** 图片目录名，与 SyncManager / rememberImageBitmap 里的约定保持一致 */
    const val DIR = "device_images"

    /**
     * 相机拍照的落点：直接在永久目录里建好空文件，返回可授权给相机 App 写入的 FileProvider Uri。
     * 因为文件本来就在永久目录内，拍完无需再拷一份。
     */
    fun newCameraTarget(context: Context): Uri {
        val file = File(imageDir(context), "photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    /**
     * 把相册选中的图片拷成永久副本，返回其 `file://` Uri 字符串；源文件读不到时返回 null。
     *
     * 在 IO 线程执行。文件名取自原 Uri、扩展名按文件头实际内容判定，
     * 因此同一张原图被反复选中只会覆盖同一份副本，不会堆积出多份。
     */
    suspend fun persistPicked(context: Context, src: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = imageDir(context)
            val base = stableBaseName(src)
            val tmp = File(dir, "$base.pending")
            context.contentResolver.openInputStream(src)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            val dest = File(dir, base + sniffExtension(tmp))
            if (dest.isFile) dest.delete()
            if (!tmp.renameTo(dest)) {
                dest.writeBytes(tmp.readBytes())
                tmp.delete()
            }
            Uri.fromFile(dest).toString()
        }.getOrNull()
    }

    private fun imageDir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    /** 取原 Uri 的最后一段当文件名主干：去掉扩展名与查询串，再滤掉不安全字符 */
    private fun stableBaseName(src: Uri): String {
        val raw = src.lastPathSegment ?: "image"
        val stem = raw.substringBefore('?').substringBeforeLast('.', raw)
        return stem.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48).ifBlank { "image" }
    }

    /** 按文件头魔数判定扩展名：相册 Uri 的末段常常只是个数字 id，不带扩展名 */
    private fun sniffExtension(file: File): String {
        val head = ByteArray(12)
        val n = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(0)
        if (n < 4) return ".jpg"
        fun b(i: Int) = head[i].toInt() and 0xFF
        return when {
            b(0) == 0xFF && b(1) == 0xD8 -> ".jpg"
            b(0) == 0x89 && b(1) == 'P'.code && b(2) == 'N'.code && b(3) == 'G'.code -> ".png"
            b(0) == 'G'.code && b(1) == 'I'.code && b(2) == 'F'.code -> ".gif"
            n >= 12 && b(0) == 'R'.code && b(1) == 'I'.code &&
                b(8) == 'W'.code && b(9) == 'E'.code && b(10) == 'B'.code && b(11) == 'P'.code -> ".webp"
            n >= 8 && b(4) == 'f'.code && b(5) == 't'.code && b(6) == 'y'.code && b(7) == 'p'.code -> ".heic"
            else -> ".jpg"
        }
    }
}
