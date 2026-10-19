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
import uz.maxcare.kids.core.limits.AppLimitRule
import uz.maxcare.kids.core.limits.AppLimitsConfig
import uz.maxcare.kids.core.limits.LimitKind

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

    @Test
    fun `events are sent with the device token`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"accepted":1}""").build())

        val result = api().uploadEvents(
            "device-token",
            listOf(OutgoingEvent("e-1", "device_booted", "2026-10-09T10:00:00Z"))
        )

        assertEquals(ApiResult.Ok(Unit), result)
        val request = server.takeRequest()
        assertEquals("/v1/device/events", request.url.encodedPath)
        assertEquals("Bearer device-token", request.headers["Authorization"])
        assertEquals(
            """{"events":[{"id":"e-1","type":"device_booted","occurredAt":"2026-10-09T10:00:00Z"}]}""",
            request.body?.utf8()
        )
    }

    @Test
    fun `locations are sent as a batch with the device token`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"accepted":2}""").build())

        val result = api().uploadLocations(
            "device-token",
            listOf(
                OutgoingLocation("l-1", 41.1, 69.2, 12.5, "2026-10-09T10:00:00Z"),
                OutgoingLocation("l-2", 41.2, 69.3, null, "2026-10-09T10:05:00Z")
            )
        )

        assertEquals(ApiResult.Ok(Unit), result)
        val request = server.takeRequest()
        assertEquals("/v1/device/locations", request.url.encodedPath)
        assertEquals("Bearer device-token", request.headers["Authorization"])
        assertEquals(
            """{"locations":[""" +
                """{"id":"l-1","latitude":41.1,"longitude":69.2,""" +
                """"accuracyMeters":12.5,"recordedAt":"2026-10-09T10:00:00Z"},""" +
                """{"id":"l-2","latitude":41.2,"longitude":69.3,""" +
                """"recordedAt":"2026-10-09T10:05:00Z"}]}""",
            request.body?.utf8()
        )
    }

    @Test
    fun `app usage is sent by day with the device token`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"accepted":1}""").build())

        val result = api().uploadAppUsage(
            "device-token",
            listOf(
                OutgoingDayUsage(
                    "2026-10-15",
                    listOf(OutgoingAppUsage("com.google.android.youtube", "YouTube", 2_400_000))
                )
            )
        )

        assertEquals(ApiResult.Ok(Unit), result)
        val request = server.takeRequest()
        assertEquals("/v1/device/app-usage", request.url.encodedPath)
        assertEquals("Bearer device-token", request.headers["Authorization"])
        assertEquals(
            """{"days":[{"date":"2026-10-15","apps":[""" +
                """{"packageName":"com.google.android.youtube","label":"YouTube",""" +
                """"foregroundMs":2400000}]}]}""",
            request.body?.utf8()
        )
    }

    @Test
    fun `app limits are read and unknown kinds skipped`() = runTest {
        // Синтетические правила — не настройки настоящей семьи.
        val body = """{"rules":[{"kind":"group","target":"video","dailyLimitMin":60,""" +
            """"sessionMin":15,"breakMin":15},{"kind":"site","target":"x"}],""" +
            """"alwaysAllowed":["com.example.school"]}"""
        server.enqueue(MockResponse.Builder().code(200).body(body).build())

        val result = api().fetchAppLimits("device-token")

        val expected = AppLimitsConfig(
            rules = listOf(AppLimitRule(LimitKind.GROUP, "video", 60, 15, 15)),
            alwaysAllowed = setOf("com.example.school")
        )
        assertEquals(ApiResult.Ok(expected), result)
        val request = server.takeRequest()
        assertEquals("/v1/device/app-limits", request.url.encodedPath)
        assertEquals("Bearer device-token", request.headers["Authorization"])
    }

    @Test
    fun `unpair is a delete with the device token and an empty answer`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertEquals(ApiResult.Ok(Unit), api().unpairDevice("device-token"))

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/v1/device", request.url.encodedPath)
        assertEquals("Bearer device-token", request.headers["Authorization"])
    }

    @Test
    fun `unpair with an unknown token is an http error`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(401).body("""{"error":"unauthorized"}""").build()
        )

        assertEquals(ApiResult.HttpError(401, "unauthorized"), api().unpairDevice("old-token"))
    }

    @Test
    fun `pairing is sent without a token`() = runTest {
        server.enqueue(pairedResponse())

        api().pairDevice("12345678", "Test phone")

        assertEquals(null, server.takeRequest().headers["Authorization"])
    }

    private fun pairedResponse() = MockResponse.Builder()
        .code(201)
        .body("""{"deviceId":"device-1","deviceToken":"token-1","recoverySecret":"AQID"}""")
        .build()
}
