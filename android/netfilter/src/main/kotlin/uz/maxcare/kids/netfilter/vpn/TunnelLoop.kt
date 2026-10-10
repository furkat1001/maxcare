package uz.maxcare.kids.netfilter.vpn

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.maxcare.kids.netfilter.dns.DnsEngine
import uz.maxcare.kids.netfilter.ip.IpCodec
import uz.maxcare.kids.netfilter.ip.UdpPacket

/**
 * Читает пакеты из VPN-интерфейса. DNS-запросы (UDP 53) обрабатываются параллельно — медленный
 * ответ провайдера не задерживает остальные. Всё прочее (DoH/DoT к публичным DNS) отбрасывается.
 *
 * Пакеты не сохраняются и не логируются: в них имена сайтов, которые открывает ребёнок.
 */
internal class TunnelLoop(
    private val input: InputStream,
    private val output: OutputStream,
    private val engine: DnsEngine,
    private val scope: CoroutineScope
) {
    /** Работает, пока туннель не закрыт: закрытие интерфейса завершает чтение. */
    fun run() {
        val buffer = ByteArray(TunnelConfig.MTU)
        while (scope.isActive) {
            val length = readOrNull(buffer) ?: break
            IpCodec.parseUdp(buffer, length)
                ?.takeIf { it.destinationPort == TunnelConfig.DNS_PORT }
                ?.let(::answerLater)
        }
    }

    private fun answerLater(request: UdpPacket) {
        scope.launch {
            val answer = engine.handle(request.payload) ?: return@launch
            write(IpCodec.buildUdpReply(request, answer))
        }
    }

    private fun write(packet: ByteArray) {
        synchronized(output) {
            try {
                output.write(packet)
            } catch (ignored: IOException) {
                // Туннель уже закрыт — ответ больше некому отдавать.
            }
        }
    }

    private fun readOrNull(buffer: ByteArray): Int? = try {
        input.read(buffer).takeIf { it >= 0 }
    } catch (ignored: IOException) {
        null
    }
}
