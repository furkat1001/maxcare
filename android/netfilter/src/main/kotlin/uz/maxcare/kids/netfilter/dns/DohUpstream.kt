package uz.maxcare.kids.netfilter.dns

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * DNS через HTTPS (RFC 8484) к семейному провайдеру: запросы уходят зашифрованными — этого требует
 * политика Google Play для VpnService. Сам MaxCare Kids исключён из VPN, поэтому запросы к
 * провайдеру не возвращаются в фильтр по кругу.
 */
internal class DohUpstream(
    client: OkHttpClient,
    private val endpoint: HttpUrl,
    private val ioDispatcher: CoroutineDispatcher
) : DnsUpstream {
    private val client = client.newBuilder()
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun query(packet: ByteArray): ByteArray? = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", DNS_MESSAGE)
            .post(packet.toRequestBody(DNS_MESSAGE.toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body.bytes() else null
            }
        } catch (ignored: IOException) {
            null
        }
    }

    companion object {
        /** Cloudflare for Families: блокирует взрослый и вредоносный контент. */
        val CLOUDFLARE_FAMILIES = "https://family.cloudflare-dns.com/dns-query".toHttpUrl()

        private const val DNS_MESSAGE = "application/dns-message"
        private const val TIMEOUT_SECONDS = 5L
    }
}
