package uz.maxcare.kids.netfilter.dns

import java.io.ByteArrayOutputStream

internal object DnsType {
    const val A = 1
    const val CNAME = 5
    const val AAAA = 28
    const val SVCB = 64
    const val HTTPS = 65
}

internal object DnsRcode {
    const val NOERROR = 0
    const val SERVFAIL = 2
    const val NXDOMAIN = 3
}

/**
 * Запрос с одним вопросом — так спрашивают системные резолверы.
 * [name] — в нижнем регистре, без завершающей точки.
 */
internal class DnsQuestion(
    val id: Int,
    val flags: Int,
    val name: String,
    val type: Int,
    val qclass: Int,
    /** Секция вопроса как пришла — копируется в ответ без изменений. */
    val questionSection: ByteArray
)

internal class DnsRecord(val type: Int, val ttl: Long, val data: ByteArray)

/**
 * Минимальный разбор и сборка DNS-сообщений (RFC 1035): только то, что нужно фильтру —
 * вопрос запроса, записи ответа и сборка ответа. Повреждённые пакеты дают null, а не исключение:
 * пакеты приходят от любых приложений телефона.
 */
internal object DnsCodec {
    // Смещения полей заголовка и записи (RFC 1035, 4.1.1 и 4.1.3).
    private const val HEADER_SIZE = 12
    private const val FLAGS = 2
    private const val QDCOUNT = 4
    private const val ANCOUNT = 6
    private const val QTYPE_QCLASS_SIZE = 4
    private const val RECORD_TTL = 4
    private const val RECORD_RDLENGTH = 8
    private const val RECORD_FIXED_SIZE = 10
    private const val ZERO_COUNTS = 3 // ANCOUNT, NSCOUNT, ARCOUNT в запросе
    private const val BITS_IN_BYTE = 8
    private const val BITS_IN_SHORT = 16
    private const val FLAG_QR = 0x8000
    private const val FLAG_RD = 0x0100
    private const val FLAG_RA = 0x0080
    private const val OPCODE_MASK = 0x7800
    private const val RCODE_MASK = 0x000F
    private const val POINTER_MASK = 0xC0
    private const val POINTER_TO_QUESTION = 0xC00C
    private const val MAX_NAME_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63
    private const val CLASS_IN = 1
    private const val BYTE_MASK = 0xFF
    private const val SHORT_MASK = 0xFFFF
    private const val UINT_MASK = 0xFFFFFFFFL

    // Ранние выходы — по одному на каждую проверку «пакет повреждён»: так читается проще.
    @Suppress("ReturnCount")
    fun parseQuery(packet: ByteArray): DnsQuestion? {
        if (packet.size < HEADER_SIZE) return null
        val flags = u16(packet, FLAGS)
        val isStandardQuery = flags and FLAG_QR == 0 && flags and OPCODE_MASK == 0
        if (!isStandardQuery || u16(packet, QDCOUNT) != 1) return null
        val (name, nameEnd) = readPlainName(packet, HEADER_SIZE) ?: return null
        val questionEnd = nameEnd + QTYPE_QCLASS_SIZE
        if (questionEnd > packet.size) return null
        return DnsQuestion(
            id = u16(packet, 0),
            flags = flags,
            name = name,
            type = u16(packet, nameEnd),
            qclass = u16(packet, nameEnd + 2),
            questionSection = packet.copyOfRange(HEADER_SIZE, questionEnd)
        )
    }

    fun rcode(response: ByteArray): Int? =
        if (response.size < HEADER_SIZE) null else u16(response, FLAGS) and RCODE_MASK

    /** Записи секции ответа; null — пакет повреждён. */
    @Suppress("ReturnCount")
    fun parseAnswers(response: ByteArray): List<DnsRecord>? {
        if (response.size < HEADER_SIZE) return null
        var pos = HEADER_SIZE
        repeat(u16(response, QDCOUNT)) {
            pos = skipName(response, pos) ?: return null
            pos += QTYPE_QCLASS_SIZE
        }
        val records = mutableListOf<DnsRecord>()
        repeat(u16(response, ANCOUNT)) {
            pos = skipName(response, pos) ?: return null
            if (pos + RECORD_FIXED_SIZE > response.size) return null
            val type = u16(response, pos)
            val ttl = u32(response, pos + RECORD_TTL)
            val length = u16(response, pos + RECORD_RDLENGTH)
            val start = pos + RECORD_FIXED_SIZE
            if (start + length > response.size) return null
            records += DnsRecord(type, ttl, response.copyOfRange(start, start + length))
            pos = start + length
        }
        return records
    }

    fun buildQuery(id: Int, name: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream()
        writeShort(out, id)
        writeShort(out, FLAG_RD)
        writeShort(out, 1)
        repeat(ZERO_COUNTS) { writeShort(out, 0) }
        name.split('.').filter { it.isNotEmpty() }.forEach { label ->
            val bytes = label.encodeToByteArray()
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        writeShort(out, type)
        writeShort(out, CLASS_IN)
        return out.toByteArray()
    }

    /** Ответ на [query]; все записи — на имя из вопроса (ссылка на него, 0xC00C). */
    fun buildResponse(query: DnsQuestion, rcode: Int, answers: List<DnsRecord>): ByteArray {
        val out = ByteArrayOutputStream()
        writeShort(out, query.id)
        writeShort(out, FLAG_QR or (query.flags and FLAG_RD) or FLAG_RA or rcode)
        writeShort(out, 1)
        writeShort(out, answers.size)
        writeShort(out, 0)
        writeShort(out, 0)
        out.write(query.questionSection)
        answers.forEach { record ->
            writeShort(out, POINTER_TO_QUESTION)
            writeShort(out, record.type)
            writeShort(out, CLASS_IN)
            writeInt(out, record.ttl)
            writeShort(out, record.data.size)
            out.write(record.data)
        }
        return out.toByteArray()
    }

    // Имя в вопросе запроса — без сжатия (так пишут резолверы); сжатие здесь считаем повреждением.
    @Suppress("ReturnCount")
    private fun readPlainName(packet: ByteArray, start: Int): Pair<String, Int>? {
        val labels = mutableListOf<String>()
        var pos = start
        var total = 0
        while (true) {
            if (pos >= packet.size) return null
            val length = packet[pos].toInt() and BYTE_MASK
            pos++
            if (length == 0) break
            if (length > MAX_LABEL_LENGTH || pos + length > packet.size) return null
            total += length + 1
            if (total > MAX_NAME_LENGTH + 1) return null
            labels += String(packet, pos, length, Charsets.US_ASCII).lowercase()
            pos += length
        }
        return labels.joinToString(".") to pos
    }

    // Пропуск имени в ответе: здесь сжатие обычное. Указатель всегда завершает имя.
    @Suppress("ReturnCount")
    private fun skipName(packet: ByteArray, start: Int): Int? {
        var pos = start
        while (pos < packet.size) {
            val length = packet[pos].toInt() and BYTE_MASK
            when {
                length == 0 -> return pos + 1

                length and POINTER_MASK == POINTER_MASK -> return if (pos + 2 <=
                    packet.size
                ) {
                    pos + 2
                } else {
                    null
                }

                length > MAX_LABEL_LENGTH -> return null

                else -> pos += length + 1
            }
        }
        return null
    }

    private fun u16(b: ByteArray, at: Int) =
        ((b[at].toInt() and BYTE_MASK) shl BITS_IN_BYTE) or (b[at + 1].toInt() and BYTE_MASK)

    private fun u32(b: ByteArray, at: Int): Long =
        ((u16(b, at).toLong() shl BITS_IN_SHORT) or u16(b, at + 2).toLong()) and UINT_MASK

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr BITS_IN_BYTE) and BYTE_MASK)
        out.write(value and BYTE_MASK)
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Long) {
        writeShort(out, ((value shr BITS_IN_SHORT) and SHORT_MASK.toLong()).toInt())
        writeShort(out, (value and SHORT_MASK.toLong()).toInt())
    }
}
