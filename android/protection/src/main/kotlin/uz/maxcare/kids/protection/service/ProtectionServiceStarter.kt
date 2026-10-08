package uz.maxcare.kids.protection.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Запуск и остановка [ProtectionService]; интерфейс — для тестов логики без Android. */
internal interface ProtectionServiceStarter {
    fun start()
    fun stop()
}

internal class AndroidProtectionServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context
) : ProtectionServiceStarter {
    private val intent get() = Intent(context, ProtectionService::class.java)

    override fun start() {
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            // С Android 12 службу нельзя запустить из фона, кроме разрешённых случаев
            // (загрузка телефона, Device Owner, видимый экран). Тогда она запустится при следующем из них.
            val notAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            if (!notAllowed) throw e
        }
    }

    override fun stop() {
        context.stopService(intent)
    }
}
