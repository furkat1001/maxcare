package uz.maxcare.kids.protection.password

import java.security.MessageDigest
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.protection.ParentPassword
import uz.maxcare.kids.core.protection.PasswordRecovery
import uz.maxcare.kids.core.protection.RecoveryResult
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.storage.writeString

/**
 * Принимает код текущего и предыдущего 5-минутного интервала (код действует 5–10 минут),
 * каждый интервал — один раз. Попытки ограничены так же, как у пароля, но отдельным счётчиком.
 */
@Singleton
internal class TotpPasswordRecovery @Inject constructor(
    private val store: SecureStore,
    private val parentPassword: StoredParentPassword,
    deviceClock: DeviceClock,
    private val wallClock: Clock,
    private val events: EventRecorder
) : PasswordRecovery {
    private val mutex = Mutex()
    private val lockout = LockoutTracker(store, deviceClock, LOCKOUT_KEY)

    override suspend fun reset(code: String, newPassword: CharArray): RecoveryResult = try {
        val normalized = code.filterNot { it.isWhitespace() || it == '-' }
        when {
            !CODE_PATTERN.matches(normalized) -> RecoveryResult.InvalidFormat

            newPassword.size < ParentPassword.MIN_LENGTH -> RecoveryResult.TooShort

            // Блокировка и проверка — под одним захватом: иначе параллельный вызов успел бы
            // проверить код между ними.
            else -> mutex.withLock { checkUnlessLocked(normalized, newPassword) }
        }
    } finally {
        newPassword.fill('\u0000')
    }

    private suspend fun checkUnlessLocked(code: String, newPassword: CharArray): RecoveryResult {
        val remaining = lockout.remaining()
        return if (remaining > Duration.ZERO) {
            RecoveryResult.LockedOut(remaining)
        } else {
            checkCode(code, newPassword)
        }
    }

    private suspend fun checkCode(code: String, newPassword: CharArray): RecoveryResult =
        when (val secret = readSecret()) {
            Secret.Missing -> RecoveryResult.NotPaired

            Secret.Unreadable -> RecoveryResult.Unavailable

            is Secret.Found -> {
                val counter = acceptedCounter(secret.bytes, code)
                if (counter != null) {
                    // Сначала гасим интервал: даже при сбое ниже этот код повторно не сработает.
                    store.writeString(LAST_USED_KEY, counter.toString())
                    parentPassword.replaceAfterRecovery(newPassword.copyOf())
                    events.record(EventType.PASSWORD_RECOVERED)
                    lockout.reset()
                    RecoveryResult.Reset
                } else {
                    val failure = lockout.registerFailure()
                    if (failure.lock > Duration.ZERO) {
                        RecoveryResult.LockedOut(failure.lock)
                    } else {
                        RecoveryResult.InvalidCode(failure.attemptsBeforeLockout)
                    }
                }
            }
        }

    private suspend fun acceptedCounter(secret: ByteArray, code: String): Long? {
        val current = RecoveryCodes.counterAt(wallClock.instant())
        val lastUsed = lastUsedCounter()
        return listOf(current, current - 1).firstOrNull { counter ->
            counter > lastUsed &&
                // Постоянное время сравнения: по скорости ответа нельзя подбирать цифры.
                MessageDigest.isEqual(
                    RecoveryCodes.code(secret, counter).encodeToByteArray(),
                    code.encodeToByteArray()
                )
        }
    }

    private suspend fun lastUsedCounter(): Long = try {
        store.readString(LAST_USED_KEY)?.toLongOrNull() ?: Long.MIN_VALUE
    } catch (ignored: SecureStoreException) {
        // Без этой записи теряется только защита от повтора уже использованного кода
        // в течение 10 минут; блокировать восстановление из-за неё хуже.
        Long.MIN_VALUE
    }

    private suspend fun readSecret(): Secret = try {
        store.read(SecureKeys.RECOVERY_SECRET)?.let { Secret.Found(it) } ?: Secret.Missing
    } catch (ignored: SecureStoreException) {
        Secret.Unreadable
    }

    private sealed interface Secret {
        class Found(val bytes: ByteArray) : Secret
        data object Missing : Secret
        data object Unreadable : Secret
    }

    private companion object {
        const val LOCKOUT_KEY = "recovery_code_lockout"
        const val LAST_USED_KEY = "recovery_code_last_counter"
        val CODE_PATTERN = Regex("[0-9]{8}")
    }
}
