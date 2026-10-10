package uz.maxcare.kids.netfilter.ip

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpCodecTest {
    private val phone4 = byteArrayOf(10, 111, 222.toByte(), 2)
    private val dns4 = byteArrayOf(10, 111, 222.toByte(), 1)
    private val phone6 = ByteArray(16) {
        if (it == 0) {
            0xFD.toByte()
        } else if (it == 15) {
            2
        } else {
            0
        }
    }
    private val dns6 = ByteArray(16) {
        if (it == 0) {
            0xFD.toByte()
        } else if (it == 15) {
            1
        } else {
            0
        }
    }
    private val query = "dns-query-bytes".encodeToByteArray()

    /** Пакет «телефон → DNS»: собираем как ответ от DNS к телефону, только наоборот. */
    private fun fromPhone(version: Int, phone: ByteArray, dns: ByteArray) = IpCodec.buildUdpReply(
        UdpPacket(
            version,
            source = dns,
            destination = phone,
            sourcePort = 53,
            destinationPort = 40000,
            query
        ),
        query
    )

    @Test
    fun `ipv4 query is parsed and the reply swaps addresses and ports`() {
        val packet = fromPhone(4, phone4, dns4)
        val request = IpCodec.parseUdp(packet, packet.size)!!
        assertArrayEquals(phone4, request.source)
        assertEquals(53, request.destinationPort)
        assertArrayEquals(query, request.payload)

        val reply = IpCodec.buildUdpReply(request, "answer".encodeToByteArray())
        val parsed = IpCodec.parseUdp(reply, reply.size)!!

        assertArrayEquals(dns4, parsed.source)
        assertArrayEquals(phone4, parsed.destination)
        assertEquals(53, parsed.sourcePort)
        assertEquals(40000, parsed.destinationPort)
        assertEquals(0xFFFF, onesComplementSum(reply, 0, 20))
        assertEquals(0xFFFF, udpSum(dns4, phone4, reply, 20))
    }

    @Test
    fun `ipv6 reply has a valid mandatory udp checksum`() {
        val packet = fromPhone(6, phone6, dns6)
        val request = IpCodec.parseUdp(packet, packet.size)!!

        val reply = IpCodec.buildUdpReply(request, "answer".encodeToByteArray())

        assertEquals(6, IpCodec.versionOf(reply, reply.size))
        assertArrayEquals(dns6, IpCodec.parseUdp(reply, reply.size)!!.source)
        assertEquals(0xFFFF, udpSum(dns6, phone6, reply, 40))
    }

    @Test
    fun `tcp, truncated and unknown packets are ignored`() {
        val packet = fromPhone(4, phone4, dns4)
        val tcp = packet.copyOf().also { it[9] = 6 }

        assertNull(IpCodec.parseUdp(tcp, tcp.size))
        assertNull(IpCodec.parseUdp(packet, 25))
        assertNull(IpCodec.parseUdp(byteArrayOf(0x70), 1))
    }

    // Независимая проверка: сумма с уже записанной контрольной суммой должна дать 0xFFFF.
    private fun onesComplementSum(
        data: ByteArray,
        offset: Int,
        length: Int,
        initial: Long = 0
    ): Int {
        var sum = initial
        var i = offset
        while (i < offset + length) {
            val hi = data[i].toInt() and 0xFF
            val lo = if (i + 1 < offset + length) data[i + 1].toInt() and 0xFF else 0
            sum += (hi shl 8) or lo
            i += 2
        }
        while (sum > 0xFFFF) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.toInt()
    }

    private fun udpSum(
        source: ByteArray,
        destination: ByteArray,
        packet: ByteArray,
        udpStart: Int
    ): Int {
        val udpLength = packet.size - udpStart
        val pseudo =
            onesComplementSum(source + destination, 0, source.size + destination.size).toLong() +
                17 + udpLength
        return onesComplementSum(packet, udpStart, udpLength, pseudo)
    }
}
