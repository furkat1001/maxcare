package uz.maxcare.kids.setup.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** То, что ViewModel узнаёт у системы; интерфейс — для тестов без Android. */
internal interface SetupEnvironment {
    fun notificationsAllowed(): Boolean

    /**
     * Геолокация доступна для фонового сбора: есть разрешение на местоположение, а с Android 10 —
     * ещё и фоновое («Разрешить всегда»). До Android 10 отдельного фонового разрешения нет.
     */
    fun locationAllowed(): Boolean

    /** Прошивка закрывает фоновые приложения: родителю нужна подсказка про автозапуск. */
    fun needsBackgroundHint(): Boolean
}

internal class AndroidSetupEnvironment @Inject constructor(
    @ApplicationContext private val context: Context
) : SetupEnvironment {
    override fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun locationAllowed(): Boolean {
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

    override fun needsBackgroundHint(): Boolean =
        Build.MANUFACTURER.lowercase() in AGGRESSIVE_VENDORS

    private companion object {
        // Xiaomi/HyperOS, Tecno, Infinix — из CLAUDE.md; остальные известны тем же поведением.
        val AGGRESSIVE_VENDORS = setOf(
            "xiaomi",
            "redmi",
            "poco",
            "tecno",
            "infinix",
            "itel",
            "oppo",
            "realme",
            "vivo",
            "huawei",
            "honor"
        )
    }
}
