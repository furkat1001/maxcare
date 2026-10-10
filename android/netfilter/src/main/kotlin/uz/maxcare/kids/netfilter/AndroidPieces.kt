package uz.maxcare.kids.netfilter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import uz.maxcare.kids.core.di.IoDispatcher
import uz.maxcare.kids.core.filter.NetworkFilter
import uz.maxcare.kids.netfilter.dns.DnsEngine
import uz.maxcare.kids.netfilter.dns.DnsPolicy
import uz.maxcare.kids.netfilter.dns.DohUpstream
import uz.maxcare.kids.netfilter.vpn.MaxCareVpnService

internal class AndroidNetworkFilter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val privateDns: PrivateDnsMonitor
) : NetworkFilter {
    override fun isPermitted(): Boolean = VpnService.prepare(context) == null

    override fun permissionIntent(): Intent? = VpnService.prepare(context)

    override fun start() {
        if (isPermitted()) send(action = null)
    }

    // VPN-службу держит система, пока туннель открыт: stopService её не остановит — закрываем изнутри.
    override fun stop() = send(MaxCareVpnService.ACTION_STOP)

    override suspend fun checkBypasses() = privateDns.check()

    private fun send(action: String?) {
        val intent = Intent(context, MaxCareVpnService::class.java).setAction(action)
        try {
            context.startService(intent)
        } catch (ignored: IllegalStateException) {
            // Запуск из фона запрещён; повторим при следующем applyPolicies (открытие, загрузка).
        }
    }
}

internal class FilterEngineFactory @Inject constructor(
    private val client: OkHttpClient,
    private val reporter: BlockReporter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    fun create() = DnsEngine(
        policy = DnsPolicy(),
        upstream = DohUpstream(client, DohUpstream.CLOUDFLARE_FAMILIES, ioDispatcher),
        onBlocked = reporter::onBlocked
    )
}

internal class SystemElapsedClock @Inject constructor() : ElapsedClock {
    override fun now(): Long = SystemClock.elapsedRealtime()
}

/** «Частный DNS» с адресом сервера: запросы идут по DNS-over-TLS мимо нашего VPN. */
internal fun interface PrivateDnsProbe {
    fun isStrictPrivateDnsOn(): Boolean
}

internal class AndroidPrivateDnsProbe @Inject constructor(
    @ApplicationContext private val context: Context
) : PrivateDnsProbe {
    // MaxCare Kids исключён из VPN, поэтому «активная сеть» для него — настоящая сеть телефона.
    override fun isStrictPrivateDnsOn(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager?.getLinkProperties(manager.activeNetwork)?.privateDnsServerName != null
    }
}

internal class AndroidBlockNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : BlockNotifier {
    // Разрешение проверено ниже, но с условием на версию: до Android 13 его нет, а lint распознаёт
    // только проверку без условия — с ней уведомление не показывалось бы на старых телефонах.
    @SuppressLint("MissingPermission")
    override fun showSiteBlocked() {
        // Без разрешения (Android 13+) уведомление не показать; блокировка при этом работает.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
                .setName(context.getString(R.string.blocked_channel_name))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build()
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_blocked)
            .setContentTitle(context.getString(R.string.blocked_title))
            .setContentText(context.getString(R.string.blocked_text))
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "blocked_sites"
        const val NOTIFICATION_ID = 2
    }
}
