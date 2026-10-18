package uz.maxcare.kids.accessibility.limits

import uz.maxcare.kids.accessibility.limits.AppTimeLimiter.Companion.EXTENSION_MS
import uz.maxcare.kids.accessibility.limits.AppTimeLimiter.Companion.MINUTE_MS
import uz.maxcare.kids.core.limits.AppLimitRule

/** Пороги предупреждения в минутах, по убыванию. */
private val WARN_AT_MIN = listOf(2, 1)

/** Засчитать [delta] мс, если в момент [since] правило не блокировало приложение. */
internal fun BucketState.credit(rule: AppLimitRule, since: Long, t: Long, delta: Long) {
    if (delta <= 0 || blockReason(rule, since) != null) return
    resetAfterRest(rule, since)
    usedTodayMs += delta
    sessionUsedMs += delta
    lastActiveAt = t
}

/** Вердикт по правилу; начинает перерыв, если сессия только что закончилась. */
internal fun BucketState.check(rule: AppLimitRule, t: Long, midnight: Long): LimitVerdict {
    // Перерыв закончился — новая сессия.
    if (breakUntil?.let { it <= t } == true) {
        breakUntil = null
        resetSession()
    }
    resetAfterRest(rule, t)
    val pause = breakUntil
    return when {
        pause != null -> LimitVerdict.Blocked(LimitReason.BREAK, pause, started = false)

        dailyExhausted(rule) -> {
            val started = !dailyReported
            dailyReported = true
            LimitVerdict.Blocked(LimitReason.DAILY, midnight, started)
        }

        sessionExhausted(rule) -> {
            val until = t + requireNotNull(rule.breakMin) * MINUTE_MS
            breakUntil = until
            LimitVerdict.Blocked(LimitReason.BREAK, until, started = true)
        }

        else -> warning(remaining(rule))
    }
}

internal fun BucketState.blockReason(rule: AppLimitRule, t: Long): LimitReason? = when {
    breakUntil?.let { it > t } == true -> LimitReason.BREAK
    dailyExhausted(rule) -> LimitReason.DAILY
    else -> null
}

/** Отсрочка ещё не использована для того, что сейчас блокирует. */
internal fun BucketState.canExtend(rule: AppLimitRule): Boolean {
    val breakOk = breakUntil == null || !sessionExtended
    val dailyOk = !dailyExhausted(rule) || !dayExtended
    return breakOk && dailyOk
}

internal fun BucketState.extend(rule: AppLimitRule, t: Long) {
    if (breakUntil?.let { it > t } == true) {
        breakUntil = null
        sessionExtraMs += EXTENSION_MS
        sessionExtended = true
        warnedAt = null
    }
    if (dailyExhausted(rule)) {
        dayExtraMs += EXTENSION_MS
        dayExtended = true
        warnedAt = null
    }
}

private fun BucketState.warning(remainingMs: Long?): LimitVerdict {
    val threshold = remainingMs?.let { left -> WARN_AT_MIN.lastOrNull { left <= it * MINUTE_MS } }
    if (threshold == null || warnedAt?.let { it <= threshold } == true) return LimitVerdict.Free
    warnedAt = threshold
    return LimitVerdict.Warn(threshold)
}

private fun BucketState.remaining(rule: AppLimitRule): Long? {
    val daily = rule.dailyLimitMin?.let { it * MINUTE_MS + dayExtraMs - usedTodayMs }
    val session = rule.sessionMin?.takeIf { rule.breakMin != null }
        ?.let { it * MINUTE_MS + sessionExtraMs - sessionUsedMs }
    return listOfNotNull(daily, session).minOrNull()
}

private fun BucketState.dailyExhausted(rule: AppLimitRule): Boolean =
    rule.dailyLimitMin?.let { usedTodayMs >= it * MINUTE_MS + dayExtraMs } == true

private fun BucketState.sessionExhausted(rule: AppLimitRule): Boolean {
    val session = rule.sessionMin
    return session != null && rule.breakMin != null &&
        sessionUsedMs >= session * MINUTE_MS + sessionExtraMs
}

/** Не пользовался приложениями правила столько же, сколько длится перерыв, — это и был перерыв. */
private fun BucketState.resetAfterRest(rule: AppLimitRule, t: Long) {
    val rest = rule.breakMin?.let { it * MINUTE_MS }
    val last = lastActiveAt
    val rested = rest != null && last != null && t - last >= rest
    if (rested && breakUntil == null && sessionUsedMs > 0) resetSession()
}

private fun BucketState.resetSession() {
    sessionUsedMs = 0
    sessionExtraMs = 0
    sessionExtended = false
    warnedAt = null
}
