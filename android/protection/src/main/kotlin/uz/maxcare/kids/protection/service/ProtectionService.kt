package uz.maxcare.kids.protection.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Foreground-служба с постоянным уведомлением «MaxCare Kids защищает этот телефон».
 *
 * Уведомление обязательно, пока защита работает: скрытый мониторинг запрещён политикой Google Play.
 * Заодно служба помогает процессу выживать на прошивках, которые агрессивно закрывают фон.
 */
class ProtectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ProtectionNotification.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            ProtectionNotification.ID,
            ProtectionNotification.build(this),
            foregroundServiceType()
        )
        // Если система закроет процесс, она перезапустит службу.
        return START_STICKY
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
}
