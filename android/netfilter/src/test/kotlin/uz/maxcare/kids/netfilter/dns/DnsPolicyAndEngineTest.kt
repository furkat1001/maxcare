package uz.maxcare.kids.netfilter.dns

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPolicyTest {
    private val policy = DnsPolicy()

    @Test
    fun `youtube goes to strict mode`() {
        listOf(
            "www.youtube.com",
            "m.youtube.com",
            "youtubei.googleapis.com",
            "www.youtube-nocookie.com"
        )
            .forEach {
                assertEquals(
                    it,
                    DnsDecision.Rewrite("restrict.youtube.com"),
                    policy.decide(it, DnsType.A)
                )
            }
    }

    @Test
    fun `search engines are forced into safe search`() {
        mapOf(
            "www.google.com" to "forcesafesearch.google.com",
            "google.co.uz" to "forcesafesearch.google.com",
            "www.google.ru" to "forcesafesearch.google.com",
            "www.bing.com" to "strict.bing.com",
            "duckduckgo.com" to "safe.duckduckgo.com",
            "yandex.uz" to "familysearch.yandex.ru",
            "ya.ru" to "familysearch.yandex.ru"
        ).forEach { (name, target) ->
            assertEquals(name, DnsDecision.Rewrite(target), policy.decide(name, DnsType.AAAA))
        }
    }

    @Test
    fun `other google services are not touched`() {
        listOf("mail.google.com", "maps.google.com", "googleapis.com", "notgoogle.com")
            .forEach { assertEquals(it, DnsDecision.Forward, policy.decide(it, DnsType.A)) }
    }

    @Test
    fun `https records of rewritten names are emptied, other types forwarded`() {
        assertEquals(DnsDecision.NoData, policy.decide("www.google.com", DnsType.HTTPS))
        assertEquals(DnsDecision.NoData, policy.decide("www.youtube.com", DnsType.SVCB))
        assertEquals(DnsDecision.Forward, policy.decide("www.google.com", 15))
    }

    @Test
    fun `gambling domains and their subdomains are blocked, look-alikes are not`() {
        assertEquals(
            DnsDecision.Block(BlockCategory.GAMBLING),
            policy.decide("1xbet.com", DnsType.A)
        )
        assertEquals(
            DnsDecision.Block(BlockCategory.GAMBLING),
            policy.decide("m.mostbet.uz", DnsType.A)
        )
        assertEquals(DnsDecision.Forward, policy.decide("not1xbet.com", DnsType.A))
    }

    @Test
    fun `firefox is told to use the system resolver`() {
        assertEquals(DnsDecision.NxDomain, policy.decide("use-application-dns.net", DnsType.A))
    }
}

class DnsEngineTest {
    private val blocked = mutableListOf<BlockCategory>()
    private val upstreamQueries = mutableListOf<String>()
    private var upstreamAnswer: (DnsQuestion) -> ByteArray? = { query ->
        DnsCodec.buildResponse(
            query,
            DnsRcode.NOERROR,
            listOf(DnsRecord(DnsType.A, 300, ipv4(93, 184, 215, 14)))
        )
    }
    private val engine = DnsEngine(DnsPolicy(), { packet ->
        val query = DnsCodec.parseQuery(packet)!!
        upstreamQueries += query.name
        upstreamAnswer(query)
    }) { blocked += it }

    private fun ipv4(vararg b: Int) = ByteArray(4) { b[it].toByte() }

    private fun ask(name: String, type: Int = DnsType.A) = DnsCodec.buildQuery(0x0101, name, type)

    @Test
    fun `ordinary names go to the provider as they are`() = runTest {
        val response = engine.handle(ask("example.com"))!!

        assertEquals(listOf("example.com"), upstreamQueries)
        assertArrayEquals(ipv4(93, 184, 215, 14), DnsCodec.parseAnswers(response)!!.single().data)
        assertTrue(blocked.isEmpty())
    }

    @Test
    fun `safe search answer carries the original name and only addresses`() = runTest {
        upstreamAnswer = { query ->
            DnsCodec.buildResponse(
                query,
                DnsRcode.NOERROR,
                listOf(
                    DnsRecord(DnsType.CNAME, 60, byteArrayOf(0)),
                    DnsRecord(DnsType.A, 60, ipv4(216, 239, 38, 120))
                )
            )
        }
        val question = ask("www.google.com")

        val response = engine.handle(question)!!

        assertEquals(listOf("forcesafesearch.google.com"), upstreamQueries)
        // Секция вопроса в ответе — исходная: www.google.com.
        assertArrayEquals(
            question.copyOfRange(12, question.size),
            response.copyOfRange(12, question.size)
        )
        val answers = DnsCodec.parseAnswers(response)!!
        assertEquals(listOf(DnsType.A), answers.map { it.type })
        assertArrayEquals(ipv4(216, 239, 38, 120), answers.single().data)
    }

    @Test
    fun `provider block is reported as filtered`() = runTest {
        upstreamAnswer = { query ->
            DnsCodec.buildResponse(
                query,
                DnsRcode.NOERROR,
                listOf(DnsRecord(DnsType.A, 60, ByteArray(4)))
            )
        }

        engine.handle(ask("blocked.example"))

        assertEquals(listOf(BlockCategory.FILTERED), blocked)
    }

    @Test
    fun `gambling is answered locally with a null address`() = runTest {
        val a = engine.handle(ask("1xbet.com"))!!
        val aaaa = engine.handle(ask("1xbet.com", DnsType.AAAA))!!

        assertTrue(upstreamQueries.isEmpty())
        assertArrayEquals(ByteArray(4), DnsCodec.parseAnswers(a)!!.single().data)
        assertArrayEquals(ByteArray(16), DnsCodec.parseAnswers(aaaa)!!.single().data)
        assertEquals(listOf(BlockCategory.GAMBLING, BlockCategory.GAMBLING), blocked)
    }

    @Test
    fun `unreachable provider gives servfail`() = runTest {
        upstreamAnswer = { null }

        assertEquals(DnsRcode.SERVFAIL, DnsCodec.rcode(engine.handle(ask("example.com"))!!))
        assertEquals(DnsRcode.SERVFAIL, DnsCodec.rcode(engine.handle(ask("www.google.com"))!!))
    }

    @Test
    fun `firefox canary is nxdomain without asking the provider`() = runTest {
        val response = engine.handle(ask("use-application-dns.net"))!!

        assertEquals(DnsRcode.NXDOMAIN, DnsCodec.rcode(response))
        assertTrue(upstreamQueries.isEmpty())
    }

    @Test
    fun `garbage is dropped`() = runTest {
        assertNull(engine.handle(byteArrayOf(1, 2, 3)))
    }
}
