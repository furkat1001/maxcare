package uz.maxcare.backend.reports

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uz.maxcare.backend.usage.AppUsageEntry

class DailyReportBuilderTest {
    private val day = LocalDate.parse("2026-10-09")
    private val at = Instant.parse("2026-10-09T10:00:00Z")

    private fun data(
        events: List<ReportEvent> = emptyList(),
        usage: List<AppUsageEntry> = emptyList(),
        earlier: Set<String>? = null,
        points: List<ReportPoint> = emptyList()
    ) = DayData(day, events, usage, earlier, points, lastSeenAt = null)

    @Test
    fun `first day has no new apps rather than all apps`() {
        val report = buildDailyReport(data(usage = listOf(AppUsageEntry("com.example.a", "A", 1))))

        assertTrue(report.screenTime.newApps.isEmpty())
    }

    @Test
    fun `apps with zero time are not shown`() {
        val report = buildDailyReport(
            data(usage = listOf(AppUsageEntry("com.example.a", "A", 0)), earlier = setOf("x"))
        )

        assertTrue(report.screenTime.topApps.isEmpty())
        assertTrue(report.screenTime.newApps.isEmpty())
    }

    @Test
    fun `limit hits carry the app label when it is known`() {
        val report = buildDailyReport(
            data(
                events = listOf(ReportEvent("app_limit_reached", "com.example.a", at)),
                usage = listOf(AppUsageEntry("com.example.a", "Alpha", 60_000))
            )
        )

        assertEquals("Alpha", report.limitsReached.single().label)
    }

    @Test
    fun `imprecise points do not add to the distance`() {
        val report = buildDailyReport(
            data(
                points = listOf(
                    ReportPoint(41.0, 69.0, 10.0, at),
                    ReportPoint(42.0, 69.0, 5_000.0, at.plusSeconds(60)),
                    ReportPoint(41.0, 69.0, 10.0, at.plusSeconds(120))
                )
            )
        )

        assertEquals(3, report.location?.points)
        assertEquals(0L, report.location?.distanceMeters)
    }

    @Test
    fun `no points means no location section`() {
        assertNull(buildDailyReport(data()).location)
    }

    @Test
    fun `unknown event types are ignored`() {
        val report = buildDailyReport(data(events = listOf(ReportEvent("something_new", null, at))))

        assertTrue(report.bypassAttempts.isEmpty())
        assertEquals(0, report.blocked.sites)
    }
}
