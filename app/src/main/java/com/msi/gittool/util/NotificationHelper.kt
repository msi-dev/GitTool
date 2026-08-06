package com.msi.gittool.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_ID = "gittool_notifications_channel"
    private const val CHANNEL_NAME = "GitTool Notifications"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT // Default importance plays a sound and displays in notifications drawer properly
            ).apply {
                description = "GitTool app general notifications"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun getAppPendingIntent(context: Context, notificationId: Int): PendingIntent {
        val intent = Intent(context, com.msi.gittool.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, notificationId, intent, flags)
    }

    fun showNotification(context: Context, title: String, message: String, notificationId: Int = System.currentTimeMillis().toInt()) {
        init(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setContentIntent(getAppPendingIntent(context, notificationId))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        manager.notify(notificationId, builder.build())
    }

    fun showProgressNotification(
        context: Context,
        title: String,
        message: String,
        progress: Int,
        max: Int,
        notificationId: Int
    ): NotificationCompat.Builder {
        init(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(progress < max && progress >= 0)
            .setAutoCancel(true)
            .setContentIntent(getAppPendingIntent(context, notificationId))
            .setProgress(max, progress, progress == -1)

        manager.notify(notificationId, builder.build())
        return builder
    }

    fun showDownloadCompleteNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int,
        fileName: String
    ) {
        init(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open Downloads
        val openIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Open",
                pendingIntent
            )
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        manager.notify(notificationId, builder.build())
    }
}

