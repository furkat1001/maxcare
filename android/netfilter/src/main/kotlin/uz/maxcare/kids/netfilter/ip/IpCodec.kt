package uz.maxcare.kids.netfilter.ip

internal class UdpPacket(
    val ipVersion: Int,
    val source: ByteArray,
    val destination: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray
)

/**
 * Пакеты из VPN-интерфейса: достаём UDP-запрос и собираем ответный пакет с правильными
 * контрольными суммами (для IPv6 UDP-сумма обязательна). Всё прочее — null.
 *
 * Смещения — по RFC 791 (IPv4), RFC 8200 (IPv6) и RFC 768 (UDP).
 */
internal object IpCodec {
    private const val IPV4 = 4
    private const val IPV6 = 6
    private const val PROTOCOL_UDP = 17

    private const val V4_MIN_HEADER = 20
    private const val V4_TOTAL_LENGTH = 2
    private const val V4_FLAGS = 6
    private const val V4_TTL = 8
    private const val V4_PROTOCOL = 9
    private const val V4_CHECKSUM = 10
    private const val V4_SOURCE = 12
    private const val V4_DESTINATION = 16
    private const val V4_ADDRESS_SIZE = 4
    private const val V4_VERSION_IHL = 0x45
    private const val DONT_FRAGMENT = 0x4000

    private const val V6_HEADER = 40
    private const val V6_PAYLOAD_LENGTH = 4
    private const val V6_NEXT_HEADER = 6
    private const val V6_HOP_LIMIT = 7
    private const val V6_SOURCE = 8
    private const val V6_DESTINATION = 24
    private const val V6_ADDRESS_SIZE = 16
    private const val V6_VERSION_BYTE = 0x60

    private const val UDP_HEADER = 8
    private const val UDP_DESTINATION_PORT = 2
    private const val UDP_LENGTH = 4
    private const val UDP_CHECKSUM = 6

    private const val DEFAULT_TTL = 64
    private const val IHL_MASK = 0x0F
    private const val WORDS_TO_BYTES = 4
    private const val VERSION_SHIFT = 4
    private const val BYTE_MASK = 0xFF
    private const val NO_CHECKSUM_SENT = 0xFFFF

    fun versionOf(packet: ByteArray, length: Int): Int? =
        if (length < 1) null else (packet[0].toInt() and BYTE_MASK) shr VERSION_SHIFT

    fun parseUdp(packet: ByteArray, length: Int): UdpPacket? = when (versionOf(packet, length)) {
        IPV4 -> parseIpv4(packet, length)
        IPV6 -> parseIpv6(packet, length)
        else -> null
    }

    fun buildUdpReply(request: UdpPacket, payload: ByteArray): ByteArray {
        val udp = ByteArray(UDP_HEADER + payload.size)
        Bytes.writeShort(udp, 0, request.destinationPort)
        Bytes.writeShort(udp, UDP_DESTINATION_PORT, request.sourcePort)
        Bytes.writeShort(udp, UDP_LENGTH, udp.size)
        payload.copyInto(udp, UDP_HEADER)

        val header = if (request.ipVersion == IPV4) {
            ipv4Header(request.destination, request.source, udp.size)
        } else {
            ipv6Header(request.destination, request.source, udp.size)
        }
        val checksum = InternetChecksum.udp(request.destination, request.source, udp)
        // Нулевая сумма в UDP означает «не посчитана» — вместо неё передаётся 0xFFFF (RFC 768).
        Bytes.writeShort(udp, UDP_CHECKSUM, if (checksum == 0) NO_CHECKSUM_SENT else checksum)
        return header + udp
    }

    private fun parseIpv4(packet: ByteArray, length: Int): UdpPacket? {
        if (length < V4_MIN_HEADER) return null
        val headerLength = (packet[0].toInt() and IHL_MASK) * WORDS_TO_BYTES
        val totalLength = Bytes.u16(packet, V4_TOTAL_LENGTH)
        val valid = headerLength >= V4_MIN_HEADER && totalLength <= length &&
            totalLength >= headerLength + UDP_HEADER && packet[V4_PROTOCOL].toInt() == PROTOCOL_UDP
        return if (valid) {
            udp(
                version = IPV4,
                source = packet.copyOfRange(V4_SOURCE, V4_SOURCE + V4_ADDRESS_SIZE),
                destination = packet.copyOfRange(V4_DESTINATION, V4_DESTINATION + V4_ADDRESS_SIZE),
                segment = packet.copyOfRange(headerLength, totalLength)
            )
        } else {
            null
        }
    }

    // Только пакеты без заголовков-расширений: DNS-запросы резолвера их не используют.
    private fun parseIpv6(packet: ByteArray, length: Int): UdpPacket? {
        val end = if (length >=
            V6_HEADER
        ) {
            V6_HEADER + Bytes.u16(packet, V6_PAYLOAD_LENGTH)
        } else {
            Int.MAX_VALUE
        }
        val valid = length >= V6_HEADER + UDP_HEADER && end <= length &&
            packet[V6_NEXT_HEADER].toInt() == PROTOCOL_UDP
        return if (valid) {
            udp(
                version = IPV6,
                source = packet.copyOfRange(V6_SOURCE, V6_SOURCE + V6_ADDRESS_SIZE),
                destination = packet.copyOfRange(V6_DESTINATION, V6_DESTINATION + V6_ADDRESS_SIZE),
                segment = packet.copyOfRange(V6_HEADER, end)
            )
        } else {
            null
        }
    }

    private fun udp(
        version: Int,
        source: ByteArray,
        destination: ByteArray,
        segment: ByteArray
    ): UdpPacket? {
        val udpLength = if (segment.size >= UDP_HEADER) Bytes.u16(segment, UDP_LENGTH) else 0
        return if (udpLength >= UDP_HEADER && udpLength <= segment.size) {
            UdpPacket(
                ipVersion = version,
                source = source,
                destination = destination,
                sourcePort = Bytes.u16(segment, 0),
                destinationPort = Bytes.u16(segment, UDP_DESTINATION_PORT),
                payload = segment.copyOfRange(UDP_HEADER, udpLength)
            )
        } else {
            null
        }
    }

    private fun ipv4Header(
        source: ByteArray,
        destination: ByteArray,
        payloadLength: Int
    ): ByteArray {
        val header = ByteArray(V4_MIN_HEADER)
        header[0] = V4_VERSION_IHL.toByte()
        Bytes.writeShort(header, V4_TOTAL_LENGTH, V4_MIN_HEADER + payloadLength)
        Bytes.writeShort(header, V4_FLAGS, DONT_FRAGMENT)
        header[V4_TTL] = DEFAULT_TTL.toByte()
        header[V4_PROTOCOL] = PROTOCOL_UDP.toByte()
        source.copyInto(header, V4_SOURCE)
        destination.copyInto(header, V4_DESTINATION)
        Bytes.writeShort(header, V4_CHECKSUM, InternetChecksum.of(header))
        return header
    }

    private fun ipv6Header(
        source: ByteArray,
        destination: ByteArray,
        payloadLength: Int
    ): ByteArray {
        val header = ByteArray(V6_HEADER)
        header[0] = V6_VERSION_BYTE.toByte()
        Bytes.writeShort(header, V6_PAYLOAD_LENGTH, payloadLength)
        header[V6_NEXT_HEADER] = PROTOCOL_UDP.toByte()
        header[V6_HOP_LIMIT] = DEFAULT_TTL.toByte()
        source.copyInto(header, V6_SOURCE)
        destination.copyInto(header, V6_DESTINATION)
        return header
    }
}

/** Контрольная сумма Интернета (RFC 1071): дополнение до единицы суммы 16-битных слов. */
internal object InternetChecksum {
    private const val PROTOCOL_UDP = 17
    private const val SHORT_MASK = 0xFFFF
    private const val CARRY_SHIFT = 16

    fun of(data: ByteArray, initial: Long = 0): Int {
        var sum = initial + sumWords(data)
        while (sum shr CARRY_SHIFT !=
            0L
        ) {
            sum = (sum and SHORT_MASK.toLong()) + (sum shr CARRY_SHIFT)
        }
        return sum.inv().toInt() and SHORT_MASK
    }

    /** С псевдозаголовком: адреса, протокол, длина UDP (RFC 768, RFC 8200 §8.1). */
    fun udp(source: ByteArray, destination: ByteArray, segment: ByteArray): Int =
        of(segment, sumWords(source) + sumWords(destination) + PROTOCOL_UDP + segment.size)

    private fun sumWords(data: ByteArray): Long {
        var sum = 0L
        var i = 0
        while (i + 1 < data.size) {
            sum += Bytes.u16(data, i)
            i += 2
        }
        if (i < data.size) sum += Bytes.u16(byteArrayOf(data[i], 0), 0)
        return sum
    }
}

internal object Bytes {
    private const val BYTE_MASK = 0xFF
    private const val BITS_IN_BYTE = 8

    fun u16(b: ByteArray, at: Int) =
        ((b[at].toInt() and BYTE_MASK) shl BITS_IN_BYTE) or (b[at + 1].toInt() and BYTE_MASK)

    fun writeShort(b: ByteArray, at: Int, value: Int) {
        b[at] = ((value shr BITS_IN_BYTE) and BYTE_MASK).toByte()
        b[at + 1] = (value and BYTE_MASK).toByte()
    }
}
