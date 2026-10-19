package uz.maxcare.kids.accessibility.limits

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import uz.maxcare.kids.core.limits.AppLimitRule
import uz.maxcare.kids.core.limits.AppLimitsConfig
import uz.maxcare.kids.core.limits.LimitKind

/** Почему приложение закрыто. */
enum class LimitReason { BREAK, DAILY }

/** Что делать с приложением на экране прямо сейчас. */
sealed interface LimitVerdict {
    data object Free : LimitVerdict

    /** Скоро блокировка — предупредить один раз на каждый порог. */
    data class Warn(val minutesLeft: Int) : LimitVerdict

    /**
     * @param untilMs когда снова можно, по тем же часам, что и [AppTimeLimiter].
     * @param started блокировка только что началась — записать событие родителю.
     */
    data class Blocked(val reason: LimitReason, val untilMs: Long, val started: Boolean) :
        LimitVerdict
}

/** Состояние одного правила за сегодня; сохраняется, чтобы пережить перезапуск. */
@Serializable
data class BucketState(
    var usedTodayMs: Long = 0,
    var sessionUsedMs: Long = 0,
    var lastActiveAt: Long? = null,
    var breakUntil: Long? = null,
    var sessionExtraMs: Long = 0,
    var dayExtraMs: Long = 0,
    var sessionExtended: Boolean = false,
    var dayExtended: Boolean = false,
    var dailyReported: Boolean = false,
    /** Самый маленький порог предупреждения (в минутах), который уже показан. */
    var warnedAt: Int? = null
)

/** @param day календарный день `2026-10-18`, к которому относятся [buckets]. */
@Serializable
data class LimiterState(val day: String, val buckets: Map<String, BucketState> = emptyMap())

/**
 * Ограничение времени в приложениях — без Android, чтобы проверять тестами.
 *
 * Время копится по правилу (ключ [AppLimitRule.key]), а не по пакету: правило на группу «видео»
 * считает YouTube и TikTok вместе. Вызывающий раз в секунду сообщает, что на экране ([tick]);
 * время между вызовами засчитывается приложению, которое было на экране.
 * Арифметика одного правила — в `LimitBucket.kt`.
 *
 * @param now доверенные часы в мс: не должны прыгать, если ребёнок переводит время.
 * @param groupOf группа приложения (`video`, `social`, `game`, `audio`) или null.
 */
class AppTimeLimiter(
    private val now: () -> Long,
    private val zone: () -> ZoneId,
    private val groupOf: (String) -> String?
) {
    /** Правила родителя; смена правил не сбрасывает уже потраченное время. */
    var config: AppLimitsConfig = AppLimitsConfig()

    /** Системные приложения, которые не блокируются никогда: звонки, SMS, лаунчер, MaxCare. */
    var exempt: Set<String> = emptySet()

    private var day: String = dayOf(now(), zone())
    private val buckets = mutableMapOf<String, BucketState>()
    private var lastTickAt: Long? = null
    private var lastPackage: String? = null

    fun restore(saved: LimiterState) {
        day = saved.day
        buckets.clear()
        saved.buckets.forEach { (key, state) -> buckets[key] = state.copy() }
        rollDayIfNeeded(now())
    }

    fun snapshot(): LimiterState = LimiterState(day, buckets.mapValues { it.value.copy() })

    /** @param packageName приложение на экране или null — экран выключен или неизвестно. */
    fun tick(packageName: String?): LimitVerdict {
        val t = now()
        rollDayIfNeeded(t)
        credit(t)
        lastTickAt = t
        lastPackage = packageName
        return packageName?.let { verdict(it, t) } ?: LimitVerdict.Free
    }

    /**
     * Сверить с системной историей: потраченное сегодня не может быть меньше. Так дневной лимит
     * переживает перезапуск и очистку данных MaxCare Kids.
     */
    fun applyBaseline(foregroundToday: Map<String, Long>) {
        rollDayIfNeeded(now())
        val totals = mutableMapOf<AppLimitRule, Long>()
        foregroundToday.forEach { (pkg, ms) ->
            rulesFor(pkg).forEach { rule -> totals[rule] = (totals[rule] ?: 0) + ms }
        }
        totals.forEach { (rule, total) ->
            val bucket = buckets.of(rule)
            bucket.usedTodayMs = maxOf(bucket.usedTodayMs, total)
        }
    }

    /** «Доиграть матч» — только для игр и один раз: за сессию для перерыва, за день для лимита. */
    fun canExtend(packageName: String): Boolean {
        if (groupOf(packageName) != GAME) return false
        val t = now()
        val blocking = rulesFor(packageName).filter { buckets.of(it).blockReason(it, t) != null }
        return blocking.isNotEmpty() && blocking.all { buckets.of(it).canExtend(it) }
    }

    fun extend(packageName: String): Boolean {
        if (!canExtend(packageName)) return false
        val t = now()
        rulesFor(packageName).forEach { buckets.of(it).extend(it, t) }
        return true
    }

    private fun credit(t: Long) {
        val pkg = lastPackage
        val since = lastTickAt
        if (pkg == null || since == null) return
        // Пропуск тиков (телефон спал, процесс тормозил) не засчитываем целиком.
        val delta = (t - since).coerceIn(0, MAX_GAP_MS)
        rulesFor(pkg).forEach { buckets.of(it).credit(it, since, t, delta) }
    }

    private fun verdict(pkg: String, t: Long): LimitVerdict {
        var blocked: LimitVerdict.Blocked? = null
        var warn: Int? = null
        val midnight = nextMidnight(t, zone())
        rulesFor(pkg).forEach { rule ->
            when (val v = buckets.of(rule).check(rule, t, midnight)) {
                is LimitVerdict.Blocked -> blocked = blocked.merge(v)
                is LimitVerdict.Warn -> warn = minOf(warn ?: v.minutesLeft, v.minutesLeft)
                LimitVerdict.Free -> Unit
            }
        }
        return blocked ?: warn?.let { LimitVerdict.Warn(it) } ?: LimitVerdict.Free
    }

    private fun rulesFor(pkg: String): List<AppLimitRule> {
        if (pkg in exempt || pkg in config.alwaysAllowed) return emptyList()
        return config.rules.filter { rule ->
            when (rule.kind) {
                LimitKind.APP -> rule.target == pkg
                LimitKind.GROUP -> rule.target == groupOf(pkg)
            }
        }
    }

    /** Новый день: потраченное обнуляется, а перерыв, начатый до полуночи, дотикивает. */
    private fun rollDayIfNeeded(t: Long) {
        val today = dayOf(t, zone())
        if (today == day) return
        day = today
        val carried = buckets.filterValues { it.breakUntil?.let { until -> until > t } == true }
            .mapValues { BucketState(breakUntil = it.value.breakUntil) }
        buckets.clear()
        buckets.putAll(carried)
    }

    companion object {
        const val GAME = "game"
        const val MINUTE_MS = 60_000L
        const val EXTENSION_MS = 5 * MINUTE_MS
        private const val MAX_GAP_MS = 5_000L
    }
}

private fun MutableMap<String, BucketState>.of(rule: AppLimitRule): BucketState =
    getOrPut(rule.key) { BucketState() }

private fun dayOf(t: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(t).atZone(zone).toLocalDate().toString()

private fun nextMidnight(t: Long, zone: ZoneId): Long = Instant.ofEpochMilli(t).atZone(zone)
    .toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

private fun LimitVerdict.Blocked?.merge(other: LimitVerdict.Blocked): LimitVerdict.Blocked {
    this ?: return other
    val longer = if (other.untilMs > untilMs) other else this
    return longer.copy(started = started || other.started)
}
