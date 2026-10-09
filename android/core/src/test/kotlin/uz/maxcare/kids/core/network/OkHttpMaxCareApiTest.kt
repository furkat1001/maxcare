package uz.maxcare.kids.core.network

import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpMaxCareApiTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun api(client: OkHttpClient = OkHttpClient()) =
        OkHttpMaxCareApi(client, server.url("/"), Dispatchers.IO)

    @Test
    fun `pairing sends code, device name and platform`() = runTest {
        server.enqueue(pairedResponse())

        api().pairDevice("12345678", "Test phone")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/devices/pair", request.url.encodedPath)
        assertTrue(request.headers["Content-Type"].orEmpty().startsWith("application/json"))
        assertEquals(
            """{"code":"12345678","deviceName":"Test phone","platform":"android"}""",
            request.body?.utf8()
        )
    }

    @Test
    fun `successful pairing returns token and decoded secret`() = runTest {
        server.enqueue(pairedResponse())

        val result = api().pairDevice("12345678", "Test phone")

        val device = (result as ApiResult.Ok).value
        assertEquals("device-1", device.deviceId)
        assertEquals("token-1", device.deviceToken)
        assertArrayEquals(byteArrayOf(1, 2, 3), device.recoverySecret)
    }

    @Test
    fun `server error code is passed through`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(400).body("""{"error":"invalid_code"}""").build()
        )

        assertEquals(
            ApiResult.HttpError(400, "invalid_code"),
            api().pairDevice("12345678", "Test phone")
        )
    }

    @Test
    fun `error without json body still has a status`() = runTest {
        server.enqueue(MockResponse.Builder().code(502).body("Bad gateway").build())

        assertEquals(ApiResult.HttpError(502, null), api().pairDevice("12345678", "Test phone"))
    }

    @Test
    fun `unparsable success response is a bad response`() = runTest {
        server.enqueue(MockResponse.Builder().code(201).body("""{"unexpected":true}""").build())

        assertEquals(ApiResult.BadResponse, api().pairDevice("12345678", "Test phone"))
    }

    @Test
    fun `invalid base64 secret is a bad response`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(201)
                .body("""{"deviceId":"d","deviceToken":"t","recoverySecret":"%%%"}""")
                .build()
        )

        assertEquals(ApiResult.BadResponse, api().pairDevice("12345678", "Test phone"))
    }

    @Test
    fun `unreachable server is no connection`() = runTest {
        val api = api()
        server.close()

        assertEquals(ApiResult.NoConnection, api.pairDevice("12345678", "Test phone"))
    }

    @Test
    fun `failed certificate check is insecure, not just offline`() = runTest {
        // Так OkHttp сообщает о сертификате, не прошедшем привязку из Network Security Config.
        val client = OkHttpClient.Builder()
            .addInterceptor { throw SSLPeerUnverifiedException("Pin verification failed") }
            .build()

        assertEquals(ApiResult.InsecureConnection, api(client).pairDevice("12345678", "Test phone"))
    }

    private fun pairedResponse() = MockResponse.Builder()
        .code(201)
        .body("""{"deviceId":"device-1","deviceToken":"token-1","recoverySecret":"AQID"}""")
        .build()
}
