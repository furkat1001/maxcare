package uz.maxcare.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uz.maxcare.backend.events.EventBatchRequest
import uz.maxcare.backend.events.EventDto
import uz.maxcare.backend.location.LocationBatchRequest
import uz.maxcare.backend.location.LocationReportRequest
import uz.maxcare.backend.reports.DailyReport
import uz.maxcare.backend.usage.AppUsageEntry
import uz.maxcare.backend.usage.AppUsageUploadRequest
import uz.maxcare.backend.usage.DayUsageRequest

/** Синтетические данные: пакеты com.example.*, координаты условного города. */
class DailyReportApiTest : ApiTestBase() {

    private fun event(type: String, at: String, detail: String? = null) =
        EventDto(UUID.randomUUID().toString(), type, at, detail)

    private suspend fun HttpClient.sendEvents(token: String, vararg events: EventDto) =
        postJson("/v1/device/events", EventBatchRequest(events.toList()), token)

    private suspend fun HttpClient.report(parent: String, deviceId: String, query: String) =
        getJson("/v1/devices/$deviceId/daily-report?$query", parent)

    @Test
    fun `report counts the parent's day, not the UTC day`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)
        // Ташкент UTC+5: день 2026-10-09 — с 2026-10-08T19:00Z до 2026-10-09T19:00Z.
        client.sendEvents(
            device.deviceToken,
            event("site_blocked_filtered", "2026-10-08T18:59:59Z", "early.example.com"),
            event("site_blocked_filtered", "2026-10-08T19:00:00Z", "m.example.com"),
            event("site_blocked_filtered", "2026-10-09T10:00:00Z", "m.example.com"),
            event("site_blocked_gambling", "2026-10-09T11:00:00Z", "bet.example.com"),
            event("search_blocked", "2026-10-09T12:00:00Z"),
            event("app_limit_reached", "2026-10-09T13:00:00Z", "com.example.video"),
            event("app_limit_reached", "2026-10-09T14:00:00Z", "com.example.video"),
            event("vpn_off", "2026-10-09T15:00:00Z"),
            event("protection_disabled", "2026-10-09T16:00:00Z"),
            event("site_blocked_filtered", "2026-10-09T19:00:00Z", "late.example.com")
        )

        val report = client.report(parent, device.deviceId, "date=2026-10-09&offsetMinutes=300")
            .body<DailyReport>()

        assertEquals(2, report.blocked.sites)
        assertEquals(1, report.blocked.gambling)
        assertEquals(1, report.blocked.search)
        assertEquals(listOf("m.example.com", "bet.example.com"), report.blocked.topSites)
        assertEquals(2, report.limitsReached.single().count)
        assertEquals("vpn_off", report.bypassAttempts.single().type)
        assertEquals(1, report.protectionDisabled)
    }

    @Test
    fun `screen time, new apps and route are summarised`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)
        client.postJson(
            "/v1/device/app-usage",
            AppUsageUploadRequest(
                listOf(
                    DayUsageRequest(
                        "2026-10-08",
                        listOf(AppUsageEntry("com.example.video", "Video", 600_000))
                    ),
                    DayUsageRequest(
                        "2026-10-09",
                        listOf(
                            AppUsageEntry("com.example.video", "Video", 1_200_000),
                            AppUsageEntry("com.example.game", "Game", 300_000)
                        )
                    )
                )
            ),
            device.deviceToken
        )
        client.postJson(
            "/v1/device/locations",
            LocationBatchRequest(
                listOf(
                    LocationReportRequest(
                        UUID.randomUUID().toString(),
                        41.0,
                        69.0,
                        10.0,
                        "2026-10-09T05:00:00Z"
                    ),
                    LocationReportRequest(
                        UUID.randomUUID().toString(),
                        41.01,
                        69.0,
                        10.0,
                        "2026-10-09T06:00:00Z"
                    )
                )
            ),
            device.deviceToken
        )

        val report = client.report(parent, device.deviceId, "date=2026-10-09&offsetMinutes=300")
            .body<DailyReport>()

        assertEquals(1_500_000, report.screenTime.totalMs)
        assertEquals("com.example.video", report.screenTime.topApps.first().packageName)
        assertEquals(listOf("com.example.game"), report.screenTime.newApps.map { it.packageName })
        assertEquals(2, report.location?.points)
        // 0,01° широты ≈ 1,1 км.
        assertEquals(true, report.location!!.distanceMeters in 1_100L..1_120L)
    }

    @Test
    fun `empty day is an empty report, not an error`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)

        val report = client.report(parent, device.deviceId, "date=2026-10-09").body<DailyReport>()

        assertEquals(0, report.screenTime.totalMs)
        assertEquals(0, report.blocked.sites)
        assertNull(report.location)
    }

    @Test
    fun `foreign device is not found and bad input is rejected`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        val stranger = client.registerAndLogin()

        assertEquals(
            HttpStatusCode.NotFound,
            client.report(stranger, device.deviceId, "date=2026-10-09").status
        )
        val owner = client.registerAndLogin()
        listOf(
            "date=yesterday",
            "",
            "date=2026-10-09&offsetMinutes=9999",
            "date=2026-10-09&offsetMinutes=x"
        )
            .forEach { query ->
                assertEquals(
                    HttpStatusCode.BadRequest,
                    client.report(owner, device.deviceId, query).status,
                    query
                )
            }
    }
}
