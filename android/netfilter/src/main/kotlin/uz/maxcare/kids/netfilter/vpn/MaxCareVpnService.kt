package uz.maxcare.kids.netfilter.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uz.maxcare.kids.core.di.ApplicationScope
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.netfilter.FilterEngineFactory

/**
 * Локальный VPN фильтра сайтов. Постоянное уведомление о защите показывает ProtectionService;
 * значок VPN в строке состояния система показывает сама — скрытого режима нет.
 */
@AndroidEntryPoint
class MaxCareVpnService : VpnService() {
    @Inject
    lateinit var events: EventRecorder

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    @Inject
    internal lateinit var engineFactory: FilterEngineFactory

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            closeTunnel()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tunnel == null) openTunnel()
        return START_STICKY
    }

    private fun openTunnel() {
        // null — нет согласия на VPN (его отозвали): работать нечем.
        val established = TunnelConfig.configure(Builder(), packageName).establish()
        if (established == null) {
            stopSelf()
            return
        }
        tunnel = established
        val loop = TunnelLoop(
            input = FileInputStream(established.fileDescriptor),
            output = FileOutputStream(established.fileDescriptor),
            engine = engineFactory.create(),
            scope = scope
        )
        scope.launch { loop.run() }
    }

    /** VPN отключили не мы: включили другой VPN или выключили наш в настройках. */
    override fun onRevoke() {
        appScope.launch { events.record(EventType.VPN_OFF) }
        closeTunnel()
        stopSelf()
    }

    override fun onDestroy() {
        closeTunnel()
        scope.cancel()
        super.onDestroy()
    }

    private fun closeTunnel() {
        tunnel?.close()
        tunnel = null
    }

    companion object {
        const val ACTION_STOP = "uz.maxcare.kids.netfilter.STOP"
    }
}
