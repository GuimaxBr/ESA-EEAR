package com.guima.esa.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guima.esa.data.UserRepository

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UserRepository.initialize(context)
        ReminderScheduler.sync(
            context = context,
            enabled = UserRepository.isReminderEnabled(),
            time = UserRepository.getReminderTime()
        )
    }
}
