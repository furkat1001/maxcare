package uz.maxcare.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uz.maxcare.backend.usage.AppUsageEntry
import uz.maxcare.backend.usage.AppUsageUploadRequest
import uz.maxcare.backend.usage.AppUsageUploadResponse
import uz.maxcare.backend.usage.DayUsageRequest
import uz.maxcare.backend.usage.DayUsageResponse

class AppUsageApiTest : ApiTestBase() {

    // Синтетические приложения и минуты — не данные настоящего ребёнка.
    private val youtube = AppUsageEntry("com.google.android.youtube", "YouTube", 40 * MINUTE)
    private val chrome = AppUsageEntry("com.android.chrome", "Chrome", 15 * MINUTE)

    private fun today(): LocalDate = clock.now.atOffset(ZoneOffset.UTC).toLocalDate()

    private suspend fun HttpClient.upload(deviceToken: String, vararg days: DayUsageRequest) =
        postJson("/v1/device/app-usage", AppUsageUploadRequest(days.toList()), deviceToken)

    private suspend fun HttpClient.usage(
        deviceId: String,
        token: String,
        date: LocalDate = today()
    ) = getJson("/v1/devices/$deviceId/app-usage?date=$date", token)

    @Test
    fun `uploading usage needs a device session`() = apiTest { client ->
        val day = DayUsageRequest(today().toString(), listOf(youtube))
        assertEquals(HttpStatusCode.Unauthorized, client.upload("not-a-token", day).status)
    }

    @Test
    fun `reading usage needs a parent session`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.getJson("/v1/devices/${device.deviceId}/app-usage?date=${today()}").status
        )
    }

    @Test
    fun `parent reads the day's usage sorted by time`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        val day = DayUsageRequest(today().toString(), listOf(chrome, youtube))
        val upload = client.upload(device.deviceToken, day)
        assertEquals(HttpStatusCode.OK, upload.status)
        assertEquals(1, upload.body<AppUsageUploadResponse>().accepted)

        val response = client.usage(device.deviceId, token)
        assertEquals(HttpStatusCode.OK, response.status)
        val usage = response.body<DayUsageResponse>()
        assertEquals(today().toString(), usage.date)
        assertEquals(listOf(youtube, chrome), usage.apps)
        assertEquals(clock.now.toString(), usage.updatedAt)
    }

    @Test
    fun `a new snapshot replaces the whole day`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)
        val date = today().toString()

        client.upload(device.deviceToken, DayUsageRequest(date, listOf(youtube, chrome)))
        val later = youtube.copy(foregroundMs = 55 * MINUTE)
        client.upload(device.deviceToken, DayUsageRequest(date, listOf(later)))

        val usage = client.usage(device.deviceId, token).body<DayUsageResponse>()
        assertEquals(listOf(later), usage.apps)
    }

    @Test
    fun `days are stored separately`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)
        val yesterday = today().minusDays(1)

        client.upload(
            device.deviceToken,
            DayUsageRequest(yesterday.toString(), listOf(chrome)),
            DayUsageRequest(today().toString(), listOf(youtube))
        )

        assertEquals(
            listOf(chrome),
            client.usage(device.deviceId, token, yesterday).body<DayUsageResponse>().apps
        )
        assertEquals(
            listOf(youtube),
            client.usage(device.deviceId, token).body<DayUsageResponse>().apps
        )
    }

    @Test
    fun `a day with no data is empty`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        val usage = client.usage(device.deviceId, token).body<DayUsageResponse>()
        assertEquals(emptyList(), usage.apps)
        assertNull(usage.updatedAt)
    }

    @Test
    fun `usage is scoped to the parent's family`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        client.upload(device.deviceToken, DayUsageRequest(today().toString(), listOf(youtube)))
        val otherParent = client.registerAndLogin()

        assertEquals(HttpStatusCode.NotFound, client.usage(device.deviceId, otherParent).status)
    }

    @Test
    fun `invalid snapshots are rejected`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        val date = today().toString()
        val invalid = listOf(
            DayUsageRequest("not-a-date", listOf(youtube)),
            DayUsageRequest(today().plusDays(3).toString(), listOf(youtube)),
            DayUsageRequest(today().minusDays(30).toString(), listOf(youtube)),
            DayUsageRequest(date, listOf(youtube.copy(foregroundMs = -1))),
            DayUsageRequest(date, listOf(youtube.copy(foregroundMs = 25 * 60 * MINUTE))),
            DayUsageRequest(date, listOf(youtube.copy(label = " "))),
            DayUsageRequest(date, listOf(youtube, youtube))
        )
        invalid.forEach { day ->
            assertEquals(
                HttpStatusCode.BadRequest,
                client.upload(device.deviceToken, day).status,
                "$day"
            )
        }
        val sameDayTwice = client.upload(
            device.deviceToken,
            DayUsageRequest(date, listOf(youtube)),
            DayUsageRequest(date, listOf(chrome))
        )
        assertEquals(HttpStatusCode.BadRequest, sameDayTwice.status)
    }

    @Test
    fun `reading needs a valid date`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        val missing = client.getJson("/v1/devices/${device.deviceId}/app-usage", token)
        assertEquals(HttpStatusCode.BadRequest, missing.status)
        val malformed = client.getJson("/v1/devices/${device.deviceId}/app-usage?date=15.10", token)
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }

    @Test
    fun `malformed device id is not found`() = apiTest { client ->
        val response = client.usage("not-a-uuid", client.registerAndLogin())
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
