package uz.maxcare.kids.protection.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import uz.maxcare.kids.protection.R

internal object ProtectionNotification {
    const val ID = 1
    const val CHANNEL_ID = "protection_status"

    /** Без звука и вибрации: уведомление висит постоянно и не должно отвлекать. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName(context.getString(R.string.protection_channel_name))
            .setDescription(context.getString(R.string.protection_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun build(context: Context): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_maxcare)
        .setContentTitle(context.getString(R.string.protection_notification_title))
        .setContentText(context.getString(R.string.protection_notification_text))
        .setContentIntent(openAppIntent(context))
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    // Главный экран приложения берём из манифеста: модуль защиты не зависит от app-kids.
    private fun openAppIntent(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        }
}
