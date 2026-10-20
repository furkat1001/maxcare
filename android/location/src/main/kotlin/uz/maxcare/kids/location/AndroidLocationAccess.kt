package uz.maxcare.kids.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import uz.maxcare.kids.core.R as CoreR

/** Те же условия, что у шага мастера «Геолокация» (`setup/.../SetupEnvironment.kt`). */
internal class AndroidLocationAccessProbe @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationAccessProbe {
    override fun current(): LocationAccess {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return when {
            !permitted() -> LocationAccess.DENIED

            manager == null || !LocationManagerCompat.isLocationEnabled(manager) ->
                LocationAccess.SWITCHED_OFF

            else -> LocationAccess.ON
        }
    }

    private fun permitted(): Boolean {
        val foreground = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            foreground && granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            foreground
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Уведомление ребёнку. Нажатие открывает настройку, которую надо поменять: переключатель
 * геолокации или разрешения MaxCare Kids. Без Google Play Services — работает и на телефонах без GMS.
 */
internal class AndroidLocationNotice @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationNotice {

    override fun show(access: LocationAccess) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.location_channel_name))
                .build()
        )
        val denied = access == LocationAccess.DENIED
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(CoreR.drawable.ic_maxcare_logo)
            .setContentTitle(
                context.getString(
                    if (denied) R.string.location_denied_title else R.string.location_off_title
                )
            )
            .setContentText(context.getString(R.string.location_notice_body))
            .setContentIntent(settingsIntent(denied))
            // Проверка повторяется каждые несколько минут: звук — только при первом показе.
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun hide() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Выключено — экран переключателя через [OpenLocationSettingsActivity] (окно доверия).
     * Нет доступа — сам MaxCare Kids: шаг мастера запросит разрешение системным диалогом.
     * Карточку приложения не открываем: в ней «Деактивировать и удалить», а защита от удаления
     * должна закрывать её всегда, когда её открыл не родитель.
     */
    private fun settingsIntent(denied: Boolean): PendingIntent {
        val intent = if (denied) {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        } else {
            Intent(context, OpenLocationSettingsActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            if (denied) 1 else 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private companion object {
        const val CHANNEL = "maxcare_location"
        const val NOTIFICATION_ID = 0x10C
    }
}
