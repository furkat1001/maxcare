package uz.maxcare.kids.setup.ui

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** То, что ViewModel узнаёт у системы; интерфейс — для тестов без Android. */
internal interface SetupEnvironment {
    fun notificationsAllowed(): Boolean

    /** Прошивка закрывает фоновые приложения: родителю нужна подсказка про автозапуск. */
    fun needsBackgroundHint(): Boolean
}

internal class AndroidSetupEnvironment @Inject constructor(
    @ApplicationContext private val context: Context
) : SetupEnvironment {
    override fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

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
