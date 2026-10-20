package uz.maxcare.kids.accessibility.limits

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.accessibility.limits.AppTimeLimiter.Companion.MINUTE_MS
import uz.maxcare.kids.core.limits.AppLimitRule
import uz.maxcare.kids.core.limits.AppLimitsConfig
import uz.maxcare.kids.core.limits.LimitKind

// Синтетические пакеты и правила — не данные настоящего ребёнка.
private const val VIDEO_A = "com.example.videoa"
private const val VIDEO_B = "com.example.videob"
private const val GAME_APP = "com.example.blockgame"
private const val DIALER = "com.example.dialer"
private const val OTHER = "com.example.notes"

class AppTimeLimiterTest {

    private var clock = Instant.parse("2026-10-18T10:00:00Z").toEpochMilli()

    private val groups = mapOf(
        VIDEO_A to "video",
        VIDEO_B to "video",
        GAME_APP to "game",
        DIALER to "video"
    )

    private fun limiter(vararg rules: AppLimitRule, allowed: Set<String> = emptySet()) =
        AppTimeLimiter({ clock }, { ZoneOffset.UTC }, { groups[it] }).apply {
            config = AppLimitsConfig(rules.toList(), allowed)
            exempt = setOf(DIALER)
        }

    /** Держать [pkg] на экране [minutes] минут, тикая раз в секунду, и вернуть последний вердикт. */
    private fun AppTimeLimiter.use(pkg: String, minutes: Double): LimitVerdict {
        var verdict = tick(pkg)
        repeat((minutes * 60).toInt()) {
            clock += 1_000
            verdict = tick(pkg)
        }
        return verdict
    }

    private fun AppTimeLimiter.verdicts(pkg: String, minutes: Int): List<LimitVerdict> {
        val all = mutableListOf(tick(pkg))
        repeat(minutes * 60) {
            clock += 1_000
            all += tick(pkg)
        }
        return all
    }

    private fun video(daily: Int? = null, session: Int? = null, pause: Int? = null) =
        AppLimitRule(LimitKind.GROUP, "video", daily, session, pause)

    @Test
    fun `apps without rules are free`() {
        val limiter = limiter(video(daily = 1))
        assertEquals(LimitVerdict.Free, limiter.use(OTHER, 5.0))
    }

    @Test
    fun `the daily limit blocks until midnight`() {
        val limiter = limiter(video(daily = 10))

        val verdict = limiter.use(VIDEO_A, 10.0)

        val midnight = Instant.parse("2026-10-19T00:00:00Z").toEpochMilli()
        assertEquals(LimitVerdict.Blocked(LimitReason.DAILY, midnight, started = true), verdict)
        // Повторное открытие — та же блокировка, но событие родителю уже записано.
        assertEquals(
            LimitVerdict.Blocked(LimitReason.DAILY, midnight, started = false),
            limiter.tick(VIDEO_A)
        )
    }

    @Test
    fun `a group counts its apps together`() {
        val limiter = limiter(video(daily = 10))

        limiter.use(VIDEO_A, 6.0)
        val verdict = limiter.use(VIDEO_B, 4.0)

        assertTrue(verdict is LimitVerdict.Blocked)
    }

    @Test
    fun `a session ends in a break and the app opens again after it`() {
        val limiter = limiter(video(session = 15, pause = 15))

        val blocked = limiter.use(VIDEO_A, 15.0)
        assertEquals(LimitReason.BREAK, (blocked as LimitVerdict.Blocked).reason)
        assertEquals(clock + 15 * MINUTE_MS, blocked.untilMs)

        clock += 10 * MINUTE_MS
        assertTrue(limiter.tick(VIDEO_B) is LimitVerdict.Blocked)

        clock += 5 * MINUTE_MS
        assertEquals(LimitVerdict.Free, limiter.use(VIDEO_A, 10.0))
    }

    @Test
    fun `resting as long as the break restarts the session`() {
        val limiter = limiter(video(session = 15, pause = 15))

        limiter.use(VIDEO_A, 10.0)
        limiter.use(OTHER, 15.0)

        assertEquals(LimitVerdict.Free, limiter.use(VIDEO_A, 12.0))
    }

    @Test
    fun `a short pause does not restart the session`() {
        val limiter = limiter(video(session = 15, pause = 15))

        limiter.use(VIDEO_A, 10.0)
        limiter.use(OTHER, 5.0)

        assertTrue(limiter.use(VIDEO_A, 5.0) is LimitVerdict.Blocked)
    }

    @Test
    fun `warnings come once at two and one minute left`() {
        val limiter = limiter(video(daily = 5))

        val verdicts = limiter.verdicts(VIDEO_A, 5)

        val warnings = verdicts.filterIsInstance<LimitVerdict.Warn>()
        assertEquals(listOf(LimitVerdict.Warn(2), LimitVerdict.Warn(1)), warnings)
        assertTrue(verdicts.last() is LimitVerdict.Blocked)
    }

    @Test
    fun `warnings come again after the parent gives more time`() {
        val limiter = limiter(video(session = 2, pause = 1))
        val early = limiter.verdicts(VIDEO_A, 1).filterIsInstance<LimitVerdict.Warn>()
        assertEquals(listOf(LimitVerdict.Warn(2), LimitVerdict.Warn(1)), early)

        // Найдено на телефоне: правило сменили на 10 минут, а «осталось 2/1 мин» не пришло.
        limiter.config = AppLimitsConfig(listOf(video(session = 10, pause = 5)))
        val later = limiter.verdicts(VIDEO_A, 9).filterIsInstance<LimitVerdict.Warn>()

        assertEquals(listOf(LimitVerdict.Warn(2), LimitVerdict.Warn(1)), later)
    }

    @Test
    fun `exempt and always-allowed apps are never blocked`() {
        val limiter = limiter(
            video(daily = 1),
            AppLimitRule(LimitKind.APP, VIDEO_B, dailyLimitMin = 1),
            allowed = setOf(VIDEO_B)
        )

        assertEquals(LimitVerdict.Free, limiter.use(DIALER, 5.0))
        assertEquals(LimitVerdict.Free, limiter.use(VIDEO_B, 5.0))
    }

    @Test
    fun `time is not counted while the screen is off or ticks stop`() {
        val limiter = limiter(video(daily = 10))

        limiter.use(VIDEO_A, 8.0)
        limiter.tick(null)
        clock += 60 * MINUTE_MS
        limiter.tick(VIDEO_A)
        // Телефон «уснул» с приложением на экране: долгий пропуск тиков не засчитывается.
        clock += 30 * MINUTE_MS

        assertFalse(limiter.tick(VIDEO_A) is LimitVerdict.Blocked)
    }

    @Test
    fun `a new day resets the time but finishes a running break`() {
        clock = Instant.parse("2026-10-18T23:50:00Z").toEpochMilli()
        val limiter = limiter(video(daily = 60, session = 5, pause = 20))

        val blocked = limiter.use(VIDEO_A, 5.0) as LimitVerdict.Blocked
        clock = Instant.parse("2026-10-19T00:05:00Z").toEpochMilli()
        assertEquals(blocked.untilMs, (limiter.tick(VIDEO_A) as LimitVerdict.Blocked).untilMs)

        clock = blocked.untilMs
        assertEquals(LimitVerdict.Free, limiter.tick(VIDEO_A))
        assertEquals(0L, limiter.snapshot().buckets.values.single().usedTodayMs)
    }

    @Test
    fun `system history raises the time already spent today`() {
        val limiter = limiter(video(daily = 30))
        // Правило уже действует (телефон его видел), история за это время выросла.
        limiter.applyBaseline(emptyMap())

        limiter.applyBaseline(
            mapOf(
                VIDEO_A to 20 * MINUTE_MS,
                VIDEO_B to 9 * MINUTE_MS,
                OTHER to 60 * MINUTE_MS
            )
        )

        assertTrue(limiter.use(VIDEO_A, 1.0) is LimitVerdict.Blocked)
    }

    @Test
    fun `a new rule counts only the time after it appeared today`() {
        val limiter = limiter(video(daily = 60))

        // До правила YouTube смотрели 70 минут — это не засчитывается.
        limiter.applyBaseline(mapOf(VIDEO_A to 70 * MINUTE_MS))
        assertTrue(limiter.use(VIDEO_A, 50.0) !is LimitVerdict.Blocked)

        // История растёт вместе с просмотром: 70 + 50 минут — по правилу всё ещё 50.
        limiter.applyBaseline(mapOf(VIDEO_A to 120 * MINUTE_MS))
        assertTrue(limiter.use(VIDEO_A, 9.0) !is LimitVerdict.Blocked)
        assertTrue(limiter.use(VIDEO_A, 1.5) is LimitVerdict.Blocked)
    }

    @Test
    fun `a new rule warns before blocking even late in the day`() {
        val limiter = limiter(video(daily = 10))
        limiter.applyBaseline(mapOf(VIDEO_A to 300 * MINUTE_MS))

        val verdicts = limiter.verdicts(VIDEO_A, 10)

        assertTrue(verdicts.any { it is LimitVerdict.Warn })
        assertTrue(verdicts.last() is LimitVerdict.Blocked)
    }

    @Test
    fun `from the next day the whole day counts`() {
        val limiter = limiter(video(daily = 30))
        limiter.applyBaseline(mapOf(VIDEO_A to 100 * MINUTE_MS))

        clock += 24 * 60 * MINUTE_MS
        limiter.applyBaseline(mapOf(VIDEO_A to 30 * MINUTE_MS))

        assertTrue(limiter.use(VIDEO_A, 0.1) is LimitVerdict.Blocked)
    }

    @Test
    fun `changing the minutes of a rule does not reset today`() {
        val limiter = limiter(video(daily = 30))
        limiter.applyBaseline(emptyMap())
        limiter.use(VIDEO_A, 25.0)

        limiter.config = AppLimitsConfig(listOf(video(daily = 20)))
        limiter.applyBaseline(mapOf(VIDEO_A to 25 * MINUTE_MS))

        assertTrue(limiter.tick(VIDEO_A) is LimitVerdict.Blocked)
    }

    @Test
    fun `a removed and re-added rule counts from the moment it is back`() {
        val limiter = limiter(video(daily = 30))
        limiter.applyBaseline(mapOf(VIDEO_A to 10 * MINUTE_MS))
        limiter.config = AppLimitsConfig()
        limiter.applyBaseline(mapOf(VIDEO_A to 40 * MINUTE_MS))

        limiter.config = AppLimitsConfig(listOf(video(daily = 30)))
        limiter.applyBaseline(mapOf(VIDEO_A to 40 * MINUTE_MS))

        assertTrue(limiter.use(VIDEO_A, 29.0) !is LimitVerdict.Blocked)
    }

    @Test
    fun `state saved before this change keeps counting the whole day`() {
        val limiter = limiter(video(daily = 30)).apply {
            restore(LimiterState(day = "2026-10-18", knownRules = null))
        }

        limiter.applyBaseline(mapOf(VIDEO_A to 30 * MINUTE_MS))

        assertTrue(limiter.tick(VIDEO_A) is LimitVerdict.Blocked)
    }

    @Test
    fun `known rules survive a restart`() {
        val first = limiter(video(daily = 30))
        first.applyBaseline(mapOf(VIDEO_A to 50 * MINUTE_MS))

        val second = limiter(video(daily = 30)).apply { restore(first.snapshot()) }
        second.applyBaseline(mapOf(VIDEO_A to 60 * MINUTE_MS))

        // После перезапуска правило не новое: 50 минут «до правила» остаются вычтенными.
        assertTrue(second.use(VIDEO_A, 19.0) !is LimitVerdict.Blocked)
        assertTrue(second.use(VIDEO_A, 1.5) is LimitVerdict.Blocked)
    }

    @Test
    fun `state survives a restart`() {
        val first = limiter(video(daily = 10))
        first.use(VIDEO_A, 9.0)

        val second = limiter(video(daily = 10)).apply { restore(first.snapshot()) }

        assertTrue(second.use(VIDEO_A, 1.0) is LimitVerdict.Blocked)
    }

    @Test
    fun `a game gets one extension per session`() {
        val limiter = limiter(AppLimitRule(LimitKind.GROUP, "game", sessionMin = 15, breakMin = 15))
        limiter.use(GAME_APP, 15.0)

        assertTrue(limiter.canExtend(GAME_APP))
        assertTrue(limiter.extend(GAME_APP))
        assertEquals(LimitVerdict.Free, limiter.tick(GAME_APP))

        assertTrue(limiter.use(GAME_APP, 5.0) is LimitVerdict.Blocked)
        assertFalse(limiter.canExtend(GAME_APP))
        assertFalse(limiter.extend(GAME_APP))
    }

    @Test
    fun `a game gets one extension of the daily limit`() {
        val limiter = limiter(AppLimitRule(LimitKind.APP, GAME_APP, dailyLimitMin = 30))
        limiter.use(GAME_APP, 30.0)

        assertTrue(limiter.extend(GAME_APP))
        assertTrue(limiter.use(GAME_APP, 5.0) is LimitVerdict.Blocked)
        assertFalse(limiter.canExtend(GAME_APP))
    }

    @Test
    fun `only games can be extended`() {
        val limiter = limiter(video(daily = 1))
        limiter.use(VIDEO_A, 1.0)

        assertFalse(limiter.canExtend(VIDEO_A))
    }
}
