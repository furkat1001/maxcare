package uz.maxcare.kids.protection.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventSync
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.filter.NetworkFilter

/**
 * Foreground-служба с постоянным уведомлением «MaxCare Kids защищает этот телефон».
 *
 * Уведомление обязательно, пока защита работает: скрытый мониторинг запрещён политикой Google Play.
 * Заодно служба помогает процессу выживать на прошивках, которые агрессивно закрывают фон.
 *
 * Раз в [SYNC_INTERVAL] проверяет уведомления и отправляет события; отправка без событий —
 * отметка «телефон на связи», по её пропаже сервер узнаёт, что приложение удалено или телефон выключен.
 */
@AndroidEntryPoint
class ProtectionService : Service() {
    @Inject
    lateinit var eventSync: EventSync

    @Inject
    lateinit var events: EventRecorder

    @Inject
    internal lateinit var notificationMonitor: NotificationStateMonitor

    @Inject
    lateinit var networkFilter: NetworkFilter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var syncLoop: Job? = null

    // Регистрируется в коде, а не в манифесте: манифестные приёмники большинство системных
    // событий с Android 8 не получают. Пока служба работает, защита включена — этого достаточно.
    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SHUTDOWN) return
            val pendingResult = goAsync()
            // Отправить уже не успеем: событие уйдёт после следующего включения.
            scope.launch {
                try {
                    events.record(EventType.DEVICE_SHUTDOWN)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            shutdownReceiver,
            IntentFilter(Intent.ACTION_SHUTDOWN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ProtectionNotification.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            ProtectionNotification.ID,
            ProtectionNotification.build(this),
            foregroundServiceType()
        )
        if (syncLoop?.isActive != true) {
            syncLoop = scope.launch {
                while (isActive) {
                    notificationMonitor.check()
                    networkFilter.checkBypasses()
                    eventSync.sync()
                    delay(SYNC_INTERVAL)
                }
            }
        }
        // Если система закроет процесс, она перезапустит службу.
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(shutdownReceiver)
        scope.cancel()
        super.onDestroy()
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    private companion object {
        // В режиме сна Android откладывает таймеры до окна обслуживания — отметки будут реже.
        val SYNC_INTERVAL = 15.minutes
    }
}
