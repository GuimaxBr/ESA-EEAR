package com.guima.esa.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.guima.esa.MainActivity
import com.guima.esa.R
import com.guima.esa.data.ProgressRepository
import com.guima.esa.data.UserRepository
import java.util.Calendar
import java.util.Date

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_NOTIFICATION_TYPE)
            ?: ReminderScheduler.TYPE_PROGRESS
        val hour = intent.getIntExtra(ReminderScheduler.EXTRA_HOUR, 20)
        val minute = intent.getIntExtra(ReminderScheduler.EXTRA_MINUTE, 0)

        ReminderScheduler.createNotificationChannel(context)

        when (type) {
            ReminderScheduler.TYPE_FAILURE_CHECK -> showFailureNotificationIfNeeded(context)
            else -> showProgressNotification(context)
        }

        ReminderScheduler.schedule(context, hour, minute)
    }

    private fun showProgressNotification(context: Context) {
        if (!canPostNotifications(context)) return

        val dailyGoal = UserRepository.getDailyGoal()
        val todaysCorrectAnswers = ProgressRepository.getTodaysCorrectAnswers()
        val dailyStudyGoalMinutes = UserRepository.getDailyStudyGoalMinutes()
        val todaysStudyMinutes = (UserRepository.getTodaysStudyTimeMs() / 60000L).toInt()

        val title = if (todaysCorrectAnswers >= dailyGoal) {
            "Meta diária quase fechada"
        } else {
            "Progresso da meta diária"
        }
        val contentText =
            "Questões: $todaysCorrectAnswers/$dailyGoal • Estudo: ${todaysStudyMinutes}/${dailyStudyGoalMinutes} min"

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$contentText. Continue estudando para bater sua meta de hoje."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(buildOpenAppIntent(context))
            .build()

        val manager = NotificationManagerCompat.from(context)
        manager.cancel(ReminderScheduler.NOTIFICATION_ID)
        manager.notify(ReminderScheduler.NOTIFICATION_ID, notification)
    }

    private fun showFailureNotificationIfNeeded(context: Context) {
        if (!canPostNotifications(context)) return

        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayDate = Date(yesterday.timeInMillis)
        val yesterdayKey = UserRepository.getDateKeyForTimestamp(yesterday.timeInMillis)

        if (UserRepository.getLastDailyFailureNoticeDate() == yesterdayKey) {
            return
        }

        val dailyGoal = UserRepository.getDailyGoal()
        val dailyStudyGoalMinutes = UserRepository.getDailyStudyGoalMinutes()
        val yesterdayCorrectAnswers = ProgressRepository.getCorrectAnswersForDate(yesterdayDate)
        val yesterdayStudyMinutes = (UserRepository.getStudyTimeForDate(yesterdayKey) / 60000L).toInt()

        val failedQuestionsGoal = yesterdayCorrectAnswers < dailyGoal
        val failedStudyGoal = yesterdayStudyMinutes < dailyStudyGoalMinutes

        if (!failedQuestionsGoal && !failedStudyGoal) {
            return
        }

        val contentText =
            "Questões: $yesterdayCorrectAnswers/$dailyGoal • Estudo: ${yesterdayStudyMinutes}/${dailyStudyGoalMinutes} min"

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Falha na meta diária")
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$contentText. Ontem a meta diária não foi concluída. Bora recuperar hoje."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(buildOpenAppIntent(context))
            .build()

        val manager = NotificationManagerCompat.from(context)
        manager.cancel(ReminderScheduler.FAILURE_NOTIFICATION_ID)
        manager.notify(ReminderScheduler.FAILURE_NOTIFICATION_ID, notification)
        UserRepository.saveLastDailyFailureNoticeDate(yesterdayKey)
    }

    private fun buildOpenAppIntent(context: Context): PendingIntent {
        val openAppIntent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            3001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            false
        } else {
            true
        }
    }
}
