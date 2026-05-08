package com.guima.esa.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object ReminderScheduler {
    const val CHANNEL_ID = "daily_study_reminder"
    const val NOTIFICATION_ID = 1001
    const val FAILURE_NOTIFICATION_ID = 1002
    private const val CHANNEL_NAME = "Lembrete diario"
    private const val REQUEST_CODE_REMINDER = 2001
    private const val REQUEST_CODE_FAILURE = 2002
    const val EXTRA_HOUR = "extra_hour"
    const val EXTRA_MINUTE = "extra_minute"
    const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
    const val TYPE_PROGRESS = "progress"
    const val TYPE_FAILURE_CHECK = "failure_check"

    fun sync(context: Context, enabled: Boolean, time: String) {
        if (!enabled) {
            cancel(context)
            return
        }

        val (hour, minute) = parseTime(time)
        schedule(context, hour, minute)
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminderPendingIntent = createReminderPendingIntent(context, hour, minute)
        val failurePendingIntent = createFailureCheckPendingIntent(context)

        alarmManager.cancel(reminderPendingIntent)
        alarmManager.cancel(failurePendingIntent)

        val triggerAtMillis = nextReminderTriggerAt(hour, minute)
        val failureTriggerAtMillis = nextFailureCheckTriggerAt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                reminderPendingIntent
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                failureTriggerAtMillis,
                failurePendingIntent
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                reminderPendingIntent
            )
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                failureTriggerAtMillis,
                failurePendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                reminderPendingIntent
            )
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                failureTriggerAtMillis,
                failurePendingIntent
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(createReminderPendingIntent(context, 0, 0))
        alarmManager.cancel(createFailureCheckPendingIntent(context))

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.cancel(FAILURE_NOTIFICATION_ID)
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificacoes diarias para estudar"
            enableVibration(true)
        }

        manager.createNotificationChannel(channel)
    }

    fun parseTime(time: String): Pair<Int, Int> {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 20
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hour to minute
    }

    private fun nextReminderTriggerAt(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis
    }

    private fun nextFailureCheckTriggerAt(): Long {
        val now = Calendar.getInstance()
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis
    }

    private fun createReminderPendingIntent(context: Context, hour: Int, minute: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
            putExtra(EXTRA_NOTIFICATION_TYPE, TYPE_PROGRESS)
        }

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createFailureCheckPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TYPE, TYPE_FAILURE_CHECK)
        }

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_FAILURE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
