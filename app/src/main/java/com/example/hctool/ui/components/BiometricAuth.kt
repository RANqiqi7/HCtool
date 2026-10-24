package com.example.hctool.ui.components

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 生物识别校验回调：第一个参数是说明文案，第二个参数收到验证结果（通过 true）。
 */
typealias BiometricVerify = (reason: String, onResult: (passed: Boolean) -> Unit) -> Unit

/**
 * 允许「指纹 / 面容」或锁屏密码二者之一通过。
 * 只用 BiometricManager 的嵌套常量（它的构造是 package-private，不能自己 new）
 */
private val AllowedAuthenticators =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * 记住一个生物识别校验器：调用后弹系统验证框，验证通过才回调 onResult(true)。
 *
 * 用于「修改订单节点状态」这类需要身份确认的操作。设备没注册指纹 / 面容时，
 * 给 Toast 说明并回调 false，避免点了没反应让人以为功能坏了。
 *
 * 这里用平台自带的 android.hardware.biometrics.BiometricPrompt（API 28+，本项目 minSdk 33 可直接用），
 * 不用 androidx.biometric：后者要求宿主是 FragmentActivity，
 * 而旧版 androidx.fragment 会对 Activity Result 的 requestCode 做 16 位校验，
 * 一点相册 / 相机就抛 "Can only use lower 16 bits for requestCode" 闪退。
 * 平台版的 BiometricManager 构造是 package-private、应用用不了，
 * 所以不做能力预检，而是直接发验证框，靠 onAuthenticationError 回报不可用 / 未录入。
 */
@Composable
fun rememberBiometricVerifier(): BiometricVerify {
    val context = LocalContext.current
    return remember(context) {
        val verify: BiometricVerify = { reason, onResult ->
            startBiometric(context, reason, onResult)
        }
        verify
    }
}

/** 发起系统验证框；未通过或无法发起时都回调 onResult(false) */
private fun startBiometric(
    context: Context,
    reason: String,
    onResult: (passed: Boolean) -> Unit,
) {
    val prompt = BiometricPrompt.Builder(context)
        .setTitle("身份验证")
        .setSubtitle(reason)
        // 允许锁屏密码兜底；设了 allowedAuthenticators 就不能再设负按钮文案
        .setAllowedAuthenticators(AllowedAuthenticators)
        .setConfirmationRequired(false)
        .build()

    prompt.authenticate(
        CancellationSignal(),
        ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            // 取消、超时、识别失败、本机不可用统一按未通过处理，不改动数据；
            // 不可用 / 未录入这类错误给用户一句提示，避免“点了没反应”
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                    toast(context, errString.toString())
                }
                onResult(false)
            }
        }
    )
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
