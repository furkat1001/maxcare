package uz.maxcare.kids.netfilter.vpn

import android.net.VpnService
import android.os.Build

/**
 * VPN «только для DNS»: в туннель попадают пакеты к нашему служебному DNS-адресу и к известным
 * публичным DNS. Остальной трафик идёт мимо VPN, как обычно, — содержимое страниц мы не видим.
 */
internal object TunnelConfig {
    const val SESSION_NAME = "MaxCare Kids"
    const val DNS_PORT = 53
    const val MTU = 1500

    private const val CLIENT_V4 = "10.111.222.2"
    private const val DNS_V4 = "10.111.222.1"
    private const val CLIENT_V6 = "fd00:6d63:6b::2"
    private const val DNS_V6 = "fd00:6d63:6b::1"
    private const val HOST_V4 = 32
    private const val HOST_V6 = 128

    /**
     * Приложения с «зашитым» DNS. Их запросы на порт 53 фильтр обрабатывает как свои, а DoH и DoT
     * на эти адреса (TCP 443/853) отбрасывает — приложение откатывается на системный DNS.
     */
    private val PUBLIC_DNS_V4 = listOf(
        "8.8.8.8", "8.8.4.4", // Google
        "1.1.1.1", "1.0.0.1", // Cloudflare
        "9.9.9.9", "149.112.112.112", // Quad9
        "208.67.222.222", "208.67.220.220", // OpenDNS
        "77.88.8.8", "77.88.8.1", // Яндекс
        "94.140.14.14", "94.140.15.15" // AdGuard
    )
    private val PUBLIC_DNS_V6 = listOf(
        "2001:4860:4860::8888",
        "2001:4860:4860::8844",
        "2606:4700:4700::1111",
        "2606:4700:4700::1001"
    )

    fun configure(builder: VpnService.Builder, ownPackage: String): VpnService.Builder {
        builder.setSession(SESSION_NAME)
            .setMtu(MTU)
            .setBlocking(true)
            .addAddress(CLIENT_V4, HOST_V4)
            .addDnsServer(DNS_V4)
            .addRoute(DNS_V4, HOST_V4)
            .addAddress(CLIENT_V6, HOST_V6)
            .addDnsServer(DNS_V6)
            .addRoute(DNS_V6, HOST_V6)
            // Сам MaxCare Kids — мимо VPN: запросы к DNS-провайдеру и серверу не уходят в фильтр по кругу.
            .addDisallowedApplication(ownPackage)
        PUBLIC_DNS_V4.forEach { builder.addRoute(it, HOST_V4) }
        PUBLIC_DNS_V6.forEach { builder.addRoute(it, HOST_V6) }
        // Иначе приложения сочтут сеть «лимитной» и, например, перестанут обновляться.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        return builder
    }
}
