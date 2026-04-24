package com.example.todoapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val tasks = TaskStorage.loadTasks(context)
        val now = Calendar.getInstance().timeInMillis

        tasks.forEach { task ->
            val reminderTime = task.reminderTimeMillis
            if (reminderTime != null && reminderTime > now && !task.isCompleted) {
                NotificationHelper.scheduleReminder(context, task)
            }
        }
    }
}