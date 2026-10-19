package uz.maxcare.kids.reports.usage

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.events.SyncResult
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.network.OutgoingDayUsage
import uz.maxcare.kids.core.network.OutgoingEvent
import uz.maxcare.kids.core.network.OutgoingLocation
import uz.maxcare.kids.core.network.PairedDevice
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore

// Синтетические приложения и время — не данные настоящего ребёнка.
private const val YOUTUBE = "com.google.android.youtube"
private const val CHROME = "com.android.chrome"
private const val MINUTE = 60_000L

/** Ташкент, UTC+5: полночь по местному времени — 19:00 UTC предыдущего дня. */
private val TASHKENT: ZoneId = ZoneId.of("Asia/Tashkent")

private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(TASHKENT).toInstant()

private fun resumed(time: String, pkg: String, activity: String = "Main") =
    ScreenEvent.Resumed(at(time), pkg, activity)

private fun paused(time: String, pkg: String, activity: String = "Main") =
    ScreenEvent.Paused(at(time), pkg, activity)

class ForegroundTimeTest {

    private val from = at("2026-10-13T00:00")
    private val to = at("2026-10-15T23:59")
    private val day = LocalDate.parse("2026-10-15")

    private fun count(vararg events: ScreenEvent, end: Instant = to) =
        foregroundTimeByDay(events.toList(), from, end, TASHKENT)

    @Test
    fun `time between resume and pause is counted`() {
        val result =
            count(resumed("2026-10-15T10:00", YOUTUBE), paused("2026-10-15T10:40", YOUTUBE))
        assertEquals(mapOf(day to mapOf(YOUTUBE to 40 * MINUTE)), result)
    }

    @Test
    fun `next app on screen closes the previous one`() {
        val result = count(
            resumed("2026-10-15T10:00", YOUTUBE),
            resumed("2026-10-15T10:10", CHROME),
            // Пауза YouTube пришла позже, чем Chrome вышел на экран: её не считаем.
            paused("2026-10-15T10:11", YOUTUBE),
            paused("2026-10-15T10:30", CHROME)
        )
        assertEquals(mapOf(YOUTUBE to 10 * MINUTE, CHROME to 20 * MINUTE), result[day])
    }

    @Test
    fun `switching windows inside one app keeps counting`() {
        val result = count(
            resumed("2026-10-15T10:00", YOUTUBE, "Main"),
            resumed("2026-10-15T10:05", YOUTUBE, "Player"),
            // Пауза первого окна после выхода второго не обрывает сессию приложения.
            paused("2026-10-15T10:06", YOUTUBE, "Main"),
            paused("2026-10-15T10:20", YOUTUBE, "Player")
        )
        assertEquals(mapOf(YOUTUBE to 20 * MINUTE), result[day])
    }

    @Test
    fun `screen off ends the session`() {
        val result = count(
            resumed("2026-10-15T21:00", YOUTUBE),
            ScreenEvent.ScreenOff(at("2026-10-15T21:15"))
        )
        assertEquals(mapOf(YOUTUBE to 15 * MINUTE), result[day])
    }

    @Test
    fun `a session over midnight is split between days`() {
        val result =
            count(resumed("2026-10-14T23:50", YOUTUBE), paused("2026-10-15T00:20", YOUTUBE))
        assertEquals(10 * MINUTE, result[LocalDate.parse("2026-10-14")]?.get(YOUTUBE))
        assertEquals(20 * MINUTE, result[day]?.get(YOUTUBE))
    }

    @Test
    fun `an open session is counted up to now`() {
        val result = count(resumed("2026-10-15T09:00", CHROME), end = at("2026-10-15T09:30"))
        assertEquals(mapOf(CHROME to 30 * MINUTE), result[day])
    }

    @Test
    fun `events are sorted before counting`() {
        val result =
            count(paused("2026-10-15T10:40", YOUTUBE), resumed("2026-10-15T10:00", YOUTUBE))
        assertEquals(mapOf(YOUTUBE to 40 * MINUTE), result[day])
    }

    @Test
    fun `a stray pause without a resume counts nothing`() {
        assertTrue(count(paused("2026-10-15T10:00", YOUTUBE)).isEmpty())
    }
}

class UsageDaysTest {

    private val apps = mapOf(YOUTUBE to "YouTube", CHROME to "Chrome")
    private val from = at("2026-10-13T00:00")
    private val now = at("2026-10-15T12:00")

    @Test
    fun `every day of the window is sent, even an empty one`() {
        val days = usageDays(
            listOf(resumed("2026-10-15T10:00", YOUTUBE), paused("2026-10-15T10:40", YOUTUBE)),
            apps,
            from,
            now,
            TASHKENT
        )
        assertEquals(listOf("2026-10-13", "2026-10-14", "2026-10-15"), days.map { it.date })
        assertTrue(days[0].apps.isEmpty())
        assertEquals(listOf("YouTube"), days[2].apps.map { it.label })
    }

    @Test
    fun `apps are sorted by time, short and unknown ones are dropped`() {
        val events = listOf(
            resumed("2026-10-15T10:00", CHROME),
            resumed("2026-10-15T10:05", YOUTUBE),
            resumed("2026-10-15T10:45", "com.android.systemui"),
            resumed("2026-10-15T11:00", CHROME),
            // 30 секунд Chrome во второй раз: в сумме 5,5 минуты.
            ScreenEvent.ScreenOff(at("2026-10-15T11:00").plusSeconds(30)),
            resumed("2026-10-15T11:10", "uz.example.short")
        )
        val day = usageDays(
            events,
            apps + ("uz.example.short" to "Short"),
            from,
            at("2026-10-15T11:10").plusSeconds(20),
            TASHKENT
        ).last()

        assertEquals(listOf(YOUTUBE, CHROME), day.apps.map { it.packageName })
        assertEquals(40 * MINUTE, day.apps[0].foregroundMs)
        assertEquals(5 * MINUTE + 30_000, day.apps[1].foregroundMs)
    }

    @Test
    fun `a blank label falls back to the package name`() {
        val day = usageDays(
            listOf(resumed("2026-10-15T10:00", YOUTUBE), paused("2026-10-15T10:40", YOUTUBE)),
            mapOf(YOUTUBE to " "),
            from,
            now,
            TASHKENT
        ).last()
        assertEquals(YOUTUBE, day.apps.single().label)
    }
}

class AppUsageUploaderTest {

    private val history = FakeHistory()
    private val api = FakeApi()
    private val store = InMemoryStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-15T07:00:00Z"), ZoneOffset.UTC)

    private fun uploader() = AppUsageUploader(history, api, store, clock, Dispatchers.Unconfined)

    private suspend fun pair() =
        store.write(SecureKeys.DEVICE_TOKEN, "device-token".encodeToByteArray())

    @Test
    fun `an unpaired phone sends nothing`() = runTest {
        assertEquals(SyncResult.NOT_PAIRED, uploader().sync())
        assertTrue(api.uploads.isEmpty())
    }

    @Test
    fun `without usage access there is nothing to send`() = runTest {
        pair()
        history.access = false
        assertEquals(SyncResult.DELIVERED, uploader().sync())
        assertTrue(api.uploads.isEmpty())
    }

    @Test
    fun `three days are sent with the device token`() = runTest {
        pair()
        assertEquals(SyncResult.DELIVERED, uploader().sync())
        assertEquals("device-token", api.lastToken)
        assertEquals(3, api.uploads.single().size)
    }

    @Test
    fun `a revoked token means not paired, other errors retry`() = runTest {
        pair()
        api.result = ApiResult.HttpError(401, "unauthorized")
        assertEquals(SyncResult.NOT_PAIRED, uploader().sync())
        api.result = ApiResult.HttpError(500, null)
        assertEquals(SyncResult.RETRY_LATER, uploader().sync())
        api.result = ApiResult.NoConnection
        assertEquals(SyncResult.RETRY_LATER, uploader().sync())
    }
}

class TodayUsageTest {

    private val history = FakeHistory()

    // 10:00 по Ташкенту.
    private val clock = Clock.fixed(at("2026-10-15T10:00"), ZoneOffset.UTC)

    private fun todayUsage() = UsageStatsTodayUsage(history, clock, Dispatchers.Unconfined)

    @Test
    fun `only today's time counts`() {
        history.screen = listOf(
            resumed("2026-10-15T08:00", YOUTUBE),
            paused("2026-10-15T08:20", YOUTUBE),
            resumed("2026-10-15T09:50", CHROME)
        )

        val today = todayUsage().today(TASHKENT)

        assertEquals(mapOf(YOUTUBE to 20 * MINUTE, CHROME to 10 * MINUTE), today)
    }

    @Test
    fun `without usage access nothing is known`() = runTest {
        history.access = false
        history.screen = listOf(resumed("2026-10-15T08:00", YOUTUBE))
        assertTrue(todayUsage().foregroundToday().isEmpty())
    }
}

private class FakeHistory : UsageHistory {
    var access = true
    var screen: List<ScreenEvent> = emptyList()

    override fun hasAccess() = access

    override fun events(from: Instant, to: Instant): List<ScreenEvent> =
        screen.filter { it.at in from..to }

    override fun launchableApps(): Map<String, String> = mapOf(YOUTUBE to "YouTube")
}

private class InMemoryStore : SecureStore {
    private val values = mutableMapOf<String, ByteArray>()

    override suspend fun read(key: String) = values[key]?.copyOf()

    override suspend fun write(key: String, value: ByteArray) {
        values[key] = value.copyOf()
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}

private class FakeApi : MaxCareApi {
    var result: ApiResult<Unit> = ApiResult.Ok(Unit)
    val uploads = mutableListOf<List<OutgoingDayUsage>>()
    var lastToken: String? = null

    override suspend fun pairDevice(code: String, deviceName: String): ApiResult<PairedDevice> =
        error("Not used")

    override suspend fun uploadEvents(
        deviceToken: String,
        events: List<OutgoingEvent>
    ): ApiResult<Unit> = error("Not used")

    override suspend fun uploadLocations(
        deviceToken: String,
        locations: List<OutgoingLocation>
    ): ApiResult<Unit> = error("Not used")

    override suspend fun uploadAppUsage(
        deviceToken: String,
        days: List<OutgoingDayUsage>
    ): ApiResult<Unit> {
        lastToken = deviceToken
        uploads += days
        return result
    }

    override suspend fun fetchFilterConfig(deviceToken: String) = error("Not used")

    override suspend fun fetchAppLimits(deviceToken: String) = error("Not used")
}
