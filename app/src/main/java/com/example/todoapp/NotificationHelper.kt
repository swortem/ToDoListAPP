package com.example.todoapp

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object NotificationHelper {

    const val CHANNEL_ID   = "todo_reminders"
    const val CHANNEL_NAME = "Task Reminders"

    /** Call once at app start (MyApp or MainActivity) */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for your to-do tasks"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /** Show a notification immediately (e.g. from BroadcastReceiver) */
    fun showNotification(context: Context, taskId: Int, title: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, taskId, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Task reminder")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(taskId, notification)
    }

    /** Schedule an exact alarm for the given task */
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleReminder(context: Context, task: Task) {
        val timeMs = task.reminderTimeMillis ?: return
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("task_id",    task.id)
            putExtra("task_title", task.title)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        val pi = PendingIntent.getBroadcast(
            context, task.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi)
    }

    /** Cancel a previously scheduled alarm */
    fun cancelReminder(context: Context, taskId: Int) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, taskId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi)
    }
}