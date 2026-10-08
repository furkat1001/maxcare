package uz.maxcare.kids.protection.password

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.storage.writeString

/**
 * Счётчик неверных попыток и блокировка ввода, сохранённые в [SecureStore].
 *
 * Отсчёт идёт по времени с момента включения телефона, а не по часам: ребёнок может перевести
 * часы, но не время работы. После перезагрузки этот отсчёт начинается с нуля, поэтому
 * незавершённая блокировка начинается заново на полный срок — перезагрузка не сокращает ожидание.
 *
 * Не потокобезопасен: вызывающий код сериализует обращения.
 */
internal class LockoutTracker(private val store: SecureStore, private val clock: DeviceClock) {
    suspend fun remaining(): Duration {
        val state = load()
        val lock = LockoutPolicy.lockDuration(state.failures)
        val bootCount = clock.bootCount()
        return when {
            lock == Duration.ZERO -> Duration.ZERO

            state.bootCount != bootCount -> {
                save(state.copy(lockedUntil = lockEnd(lock), bootCount = bootCount))
                lock
            }

            else -> (state.lockedUntil - clock.elapsedRealtime()).coerceAtLeast(0).milliseconds
        }
    }

    /** @return блокировка, включённая этой ошибкой, или [Duration.ZERO]; и сколько попыток осталось до неё. */
    suspend fun registerFailure(): Failure {
        val failures = (load().failures + 1).coerceAtMost(LockoutPolicy.MAX_FAILURES)
        val lock = LockoutPolicy.lockDuration(failures)
        val lockedUntil = if (lock > Duration.ZERO) lockEnd(lock) else 0
        save(LockoutState(failures, lockedUntil, clock.bootCount()))
        val attemptsLeft = LockoutPolicy.FIRST_LOCKING_FAILURE - failures
        return Failure(lock = lock, attemptsBeforeLockout = attemptsLeft.coerceAtLeast(0))
    }

    suspend fun reset() = save(LockoutState.NONE)

    data class Failure(val lock: Duration, val attemptsBeforeLockout: Int)

    private suspend fun load(): LockoutState = try {
        store.readString(KEY)?.let { LockoutState.parse(it) ?: strictest() } ?: LockoutState.NONE
    } catch (ignored: SecureStoreException) {
        strictest()
    }

    // Счётчик повреждён или подменён — самая строгая блокировка, а не сброс попыток.
    private suspend fun strictest(): LockoutState {
        val state = LockoutState(
            failures = LockoutPolicy.MAX_FAILURES,
            lockedUntil = lockEnd(LockoutPolicy.lockDuration(LockoutPolicy.MAX_FAILURES)),
            bootCount = clock.bootCount()
        )
        try {
            save(state)
        } catch (ignored: SecureStoreException) {
            // Записать нельзя — блокировка будет пересчитываться при каждом обращении.
        }
        return state
    }

    private suspend fun save(state: LockoutState) {
        if (state == LockoutState.NONE) {
            store.remove(KEY)
        } else {
            store.writeString(KEY, state.serialize())
        }
    }

    private fun lockEnd(lock: Duration) = clock.elapsedRealtime() + lock.inWholeMilliseconds

    private companion object {
        const val KEY = "parent_password_lockout"
    }
}
