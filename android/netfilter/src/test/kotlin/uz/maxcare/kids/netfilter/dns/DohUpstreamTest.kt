package uz.maxcare.kids.netfilter.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DohUpstreamTest {
    private val server = MockWebServer()
    private val query = DnsCodec.buildQuery(7, "example.com", DnsType.A)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun upstream() = DohUpstream(OkHttpClient(), server.url("/dns-query"), Dispatchers.IO)

    @Test
    fun `query is posted as a dns message and the answer returned as is`() = runTest {
        val answer = byteArrayOf(0, 7, 0x81.toByte(), 0x80.toByte())
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(answer)).build())

        assertArrayEquals(answer, upstream().query(query))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("application/dns-message", request.headers["Content-Type"])
        assertEquals("application/dns-message", request.headers["Accept"])
        assertEquals(query.toByteString(), request.body)
    }

    @Test
    fun `provider error and unreachable provider give null`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        assertNull(upstream().query(query))

        val unreachable = upstream()
        server.close()
        assertNull(unreachable.query(query))
    }
}
