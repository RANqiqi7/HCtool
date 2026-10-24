package com.example.hctool.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.hctool.R
import com.example.hctool.ui.device.rememberImageBitmap
import com.example.hctool.ui.theme.LocalAppSettings

/**
 * 全站统一的背景图绘制：主页面、设备详情、订单详情共用一个入口，
 * 换背景只需改这一处逻辑。
 *
 * - 设置了自定义背景（[LocalAppSettings] 的 backgroundUri）→ 解码该 Uri 平铺整屏；
 *   解码中 / 失败时回退内置默认图，避免闪白。
 * - 未设置 → 内置 bg_main。
 * - 预览态（layoutlib）不解码大图，退化为纯白底。
 *
 * @param inPreview 预览态标志，由调用方传入 LocalInspectionMode.current
 */
@Composable
fun AppBackgroundImage(
    modifier: Modifier = Modifier,
    inPreview: Boolean = false,
) {
    if (inPreview) {
        Box(modifier = modifier.background(Color.White))
        return
    }
    val customUri = LocalAppSettings.current.backgroundUri
    // 背景铺满整屏，取较大的采样边长保证清晰度，同时避免解码超大原图 OOM
    val bitmap = rememberImageBitmap(customUri, maxDimension = 1440)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(R.drawable.bg_main),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}
