package uz.maxcare.kids.netfilter.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsCodecTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // Эталон: запрос A для example.com с id 0x1234 и флагом RD (RFC 1035, раздел 4).
    private val exampleQuery = bytes(
        0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x07, 'e'.code, 'x'.code, 'a'.code, 'm'.code, 'p'.code, 'l'.code, 'e'.code,
        0x03, 'c'.code, 'o'.code, 'm'.code, 0x00,
        0x00, 0x01, 0x00, 0x01
    )

    @Test
    fun `query is built byte for byte and names are lowercased`() {
        assertArrayEquals(exampleQuery, DnsCodec.buildQuery(0x1234, "example.com", DnsType.A))
        val parsed = DnsCodec.parseQuery(DnsCodec.buildQuery(1, "WWW.Google.COM", DnsType.AAAA))!!
        assertEquals("www.google.com", parsed.name)
        assertEquals(DnsType.AAAA, parsed.type)
    }

    @Test
    fun `query is parsed`() {
        val query = DnsCodec.parseQuery(exampleQuery)!!

        assertEquals(0x1234, query.id)
        assertEquals("example.com", query.name)
        assertEquals(DnsType.A, query.type)
        assertEquals(1, query.qclass)
    }

    @Test
    fun `response points answers at the question and keeps id and RD`() {
        val query = DnsCodec.parseQuery(exampleQuery)!!

        val response = DnsCodec.buildResponse(
            query,
            DnsRcode.NOERROR,
            listOf(DnsRecord(DnsType.A, 300, bytes(93, 184, 215, 14)))
        )

        val expected =
            bytes(0x12, 0x34, 0x81, 0x80, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00) +
                exampleQuery.copyOfRange(12, exampleQuery.size) +
                bytes(0xC0, 0x0C, 0x00, 0x01, 0x00, 0x01) + // указатель на вопрос, A, IN
                bytes(0x00, 0x00, 0x01, 0x2C, 0x00, 0x04, 93, 184, 215, 14) // TTL 300, 4 байта
        assertArrayEquals(expected, response)
        assertEquals(DnsRcode.NOERROR, DnsCodec.rcode(response))
    }

    @Test
    fun `answers with compressed names are parsed`() {
        // www.google.com CNAME forcesafesearch.google.com (сжато: 16 байт метки + 2 указателя = 0x12)
        // + A 216.239.38.120, имя которой — указатель на данные CNAME (смещение 0x2C).
        val response = bytes(
            0x00, 0x01, 0x81, 0x80, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00,
            0x03, 'w'.code, 'w'.code, 'w'.code,
            0x06, 'g'.code, 'o'.code, 'o'.code, 'g'.code, 'l'.code, 'e'.code,
            0x03, 'c'.code, 'o'.code, 'm'.code, 0x00, 0x00, 0x01, 0x00, 0x01,
            0xC0, 0x0C, 0x00, 0x05, 0x00, 0x01, 0x00, 0x00, 0x00, 0x3C, 0x00, 0x12,
            0x0F, 'f'.code, 'o'.code, 'r'.code, 'c'.code, 'e'.code,
            's'.code, 'a'.code, 'f'.code, 'e'.code,
            's'.code, 'e'.code, 'a'.code, 'r'.code, 'c'.code, 'h'.code, 0xC0, 0x10,
            0xC0, 0x2C, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x3C,
            0x00, 0x04, 216, 239, 38, 120
        )

        val answers = DnsCodec.parseAnswers(response)!!

        assertEquals(listOf(DnsType.CNAME, DnsType.A), answers.map { it.type })
        assertArrayEquals(bytes(216, 239, 38, 120), answers[1].data)
        assertEquals(60L, answers[1].ttl)
    }

    @Test
    fun `damaged packets are rejected, not thrown`() {
        val longLabel =
            bytes(0, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 64) + ByteArray(64) + bytes(0, 0, 1, 0, 1)
        listOf(
            ByteArray(5),
            exampleQuery.copyOf(20),
            longLabel,
            exampleQuery.copyOf().also { it[2] = 0x81.toByte() }, // это ответ, а не запрос
            exampleQuery.copyOf().also { it[5] = 2 } // два вопроса
        ).forEach { assertNull(DnsCodec.parseQuery(it)) }
        assertNull(DnsCodec.parseAnswers(exampleQuery.copyOf().also { it[7] = 1 }))
    }
}
