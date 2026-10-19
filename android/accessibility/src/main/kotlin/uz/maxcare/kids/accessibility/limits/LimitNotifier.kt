package uz.maxcare.kids.accessibility.limits

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uz.maxcare.kids.accessibility.R
import uz.maxcare.kids.core.R as CoreR

/** Предупреждение ребёнку: «осталось 2 минуты» — чтобы успеть сохраниться и досмотреть. */
internal class LimitNotifier(private val context: Context) {

    fun warn(packageName: String, minutesLeft: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.limit_channel_name))
                .build()
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(CoreR.drawable.ic_maxcare_logo)
            .setContentTitle(
                context.getString(R.string.limit_warning, labelOf(packageName), minutesLeft)
            )
            .setContentText(context.getString(R.string.limit_warning_body))
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()
        // Одно уведомление на приложение: новое предупреждение заменяет прежнее.
        manager.notify(packageName.hashCode(), notification)
    }

    private fun labelOf(packageName: String): CharSequence = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0))
    } catch (ignored: PackageManager.NameNotFoundException) {
        packageName
    }

    private companion object {
        const val CHANNEL = "maxcare_app_limits"
        const val TIMEOUT_MS = 2 * 60_000L
    }
}
