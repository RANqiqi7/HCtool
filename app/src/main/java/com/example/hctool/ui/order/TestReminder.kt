package com.example.hctool.ui.order

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.hctool.MainActivity
import com.example.hctool.R
import com.example.hctool.ui.data.LocalDataStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 测试到期提醒：订单「测试」节点进行中时，按预计结束时间（开始 + testHours）
 * 在其**当天早上 9:00** 发一条系统通知；9:00 落在周末则自动顺延到下一个周一
 * （周一至周五视为工作日）。
 *
 * - 触发时间只由订单数据推导，App 每次数据落盘后重算（见 MainActivity 的钩子），
 *   勾选完成 / 撤销 / 改时长都会自然取消或改排旧的提醒。
 * - 用 AlarmManager 精确闹钟 + BroadcastReceiver 发通知，不引入额外依赖
 *   （本地 gradle 缓存没有 WorkManager；分钟级精度的单次提醒用原生闹钟更贴合）。
 * - 同一条提醒按「订单 id + 触发日」在 SharedPreferences 里去重，
 *   重启广播 / 重复触发不会重复弹。
 */

/** 一条待触发的测试提醒 */
data class TestReminder(
    val orderId: String,
    val orderNo: String,
    /** 预计结束时间原文（yyyy-MM-dd HH:mm），通知文案用 */
    val expectedEnd: String,
    /** 通知触发时刻（epoch millis）：预计结束当天 09:00，非工作日顺延 */
    val triggerAt: Long,
)

/** 提醒触发小时：早上 9 点 */
private const val REMINDER_HOUR = 9

/**
 * 一条订单的提醒触发时刻：预计结束当天 9:00；落在周六 / 周日则顺延到周一。
 * 时间解析复用 expectedEndTime（非法输入返回空串）。
 */
fun reminderTriggerAt(expectedEnd: String): Long? {
    if (expectedEnd.isBlank()) return null
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val end = runCatching { fmt.parse(expectedEnd) }.getOrNull() ?: return null
    val cal = Calendar.getInstance().apply {
        time = end
        set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
        cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    ) {
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return cal.timeInMillis
}

/**
 * 从订单列表算出全部待提醒：测试进行中、testHours 合法、预计结束日 >= 今天
 * （已过那天即过期，不再提醒）。纯函数，便于单测与广播侧复用。
 */
fun pendingTestReminders(orders: List<Order>, now: Long = System.currentTimeMillis()): List<TestReminder> =
    orders.mapNotNull { order ->
        val test = order.nodes.firstOrNull { it.name == "测试" } ?: return@mapNotNull null
        if (test.state != OrderNodeState.IN_PROGRESS) return@mapNotNull null
        val expectedEnd = expectedEndTime(test.startTime, order.testHours)
        if (expectedEnd.isBlank()) return@mapNotNull null
        val triggerAt = reminderTriggerAt(expectedEnd) ?: return@mapNotNull null
        if (triggerAt < now) {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            if (triggerAt < todayStart) return@mapNotNull null
        }
        TestReminder(order.id, order.orderNo, expectedEnd, triggerAt)
    }

/**
 * 调度与通知的静态入口：MainActivity 数据落盘后调 [reschedule]，
 * 闹钟广播与开机广播打到 [TestReminderReceiver] 后也回到这里处理。
 */
object TestReminders {
    private const val TAG = "TestReminder"
    private const val CHANNEL_ID = "test_reminder"
    private const val REQUEST_CODE = 42001
    private const val SP_NAME = "hctool_reminder"
    private const val KEY_SENT_PREFIX = "sent_"

    /** 内部触发的广播 action，Manifest 中以它注册接收器 */
    const val ACTION_TEST_DUE = "com.example.hctool.ACTION_TEST_DUE"

    /** 重算并登记下一次提醒：取消旧闹钟，取未发送提醒中最早的未来触发时刻排精确闹钟 */
    fun reschedule(context: Context) {
        val snapshot = LocalDataStore.load(context) ?: return
        val now = System.currentTimeMillis()
        val pending = pendingTestReminders(snapshot.orders).filter { !isSent(context, it) }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.cancel(pendingIntent(context))
        val next = pending.filter { it.triggerAt > now }.minByOrNull { it.triggerAt }
        if (next == null) {
            Log.d(TAG, "reschedule: 没有待触发的提醒，已取消闹钟")
            return
        }
        val pi = pendingIntent(context)
        // SCHEDULE_EXACT_ALARM 已在 Manifest 声明；个别用户在系统设置里关掉时退化为非精确闹钟
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, next.triggerAt, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, next.triggerAt, pi)
        }
        Log.d(
            TAG, "reschedule: 下次提醒 ${next.orderNo} 于 ${
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(next.triggerAt))
            }"
        )
    }

    /** 闹钟 / 开机广播入口：补发所有已到期待提醒，然后接着排下一次 */
    fun handleDue(context: Context) {
        val snapshot = LocalDataStore.load(context) ?: return
        val now = System.currentTimeMillis()
        pendingTestReminders(snapshot.orders)
            .filter { it.triggerAt <= now && !isSent(context, it) }
            .forEach { remindOne(context, it) }
        reschedule(context)
    }

    /** 发一条通知并按「订单 id + 触发日」记账去重 */
    private fun remindOne(context: Context, reminder: TestReminder) {
        createChannel(context)
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("测试到期提醒")
            .setContentText("订单 ${reminder.orderNo} 测试预计 ${reminder.expectedEnd} 结束，记得完成节点")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "订单 ${reminder.orderNo} 的测试预计 ${reminder.expectedEnd} 结束，" +
                        "今天记得把测试节点改为完成。"
                )
            )
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(reminder.orderId.hashCode(), notification)
            markSent(context, reminder)
            Log.d(TAG, "已提醒: ${reminder.orderNo}")
        } catch (e: SecurityException) {
            // 用户未授予通知权限：不发、不记账，下次到期还会再试
            Log.w(TAG, "无通知权限，跳过 ${reminder.orderNo}")
        }
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "测试提醒", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    /** 去重标记：订单 id + 触发日 yyyyMMdd */
    private fun sentKey(context: Context, reminder: TestReminder): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(reminder.triggerAt))
        return KEY_SENT_PREFIX + reminder.orderId + "_" + day
    }

    private fun isSent(context: Context, reminder: TestReminder): Boolean =
        sp(context).getBoolean(sentKey(context, reminder), false)

    private fun markSent(context: Context, reminder: TestReminder) {
        sp(context).edit().putBoolean(sentKey(context, reminder), true).apply()
    }

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TestReminderReceiver::class.java).apply { action = ACTION_TEST_DUE }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * 提醒广播接收器：闹钟到点（ACTION_TEST_DUE）与开机（BOOT_COMPLETED）都进入
 * [TestReminders.handleDue] 重算补发。Manifest 已声明 exported。
 */
class TestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TestReminders.ACTION_TEST_DUE, Intent.ACTION_BOOT_COMPLETED ->
                TestReminders.handleDue(context)
        }
    }
}
