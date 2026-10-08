package uz.maxcare.kids.protection.password

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.maxcare.kids.core.di.DefaultDispatcher
import uz.maxcare.kids.core.protection.ChangePasswordResult
import uz.maxcare.kids.core.protection.ParentPassword
import uz.maxcare.kids.core.protection.PasswordCheck
import uz.maxcare.kids.core.protection.SetPasswordResult
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.storage.writeString

/**
 * Хеш пароля хранится в [SecureStore], блокировка ввода — в [LockoutTracker]. Все операции идут
 * под одним мьютексом: две параллельные проверки не должны обойти счётчик попыток.
 */
@Singleton
internal class StoredParentPassword @Inject constructor(
    private val store: SecureStore,
    private val hasher: PasswordHasher,
    clock: DeviceClock,
    @DefaultDispatcher private val hashingDispatcher: CoroutineDispatcher
) : ParentPassword {
    private val mutex = Mutex()
    private val lockout = LockoutTracker(store, clock)

    override suspend fun isSet(): Boolean = try {
        store.read(HASH_KEY) != null
    } catch (ignored: SecureStoreException) {
        // Запись есть, но не читается — считаем пароль заданным, иначе это был бы обход.
        true
    }

    override suspend fun setInitial(password: CharArray): SetPasswordResult = password.wipeAfter {
        mutex.withLock {
            when {
                isSet() -> SetPasswordResult.AlreadySet

                password.size < ParentPassword.MIN_LENGTH -> SetPasswordResult.TooShort

                else -> {
                    saveHash(password)
                    SetPasswordResult.Saved
                }
            }
        }
    }

    override suspend fun verify(password: CharArray): PasswordCheck = password.wipeAfter {
        mutex.withLock { verifyLocked(password) }
    }

    override suspend fun change(current: CharArray, new: CharArray): ChangePasswordResult =
        current.wipeAfter {
            new.wipeAfter {
                mutex.withLock {
                    // Длину нового пароля проверяем до проверки текущего, чтобы не тратить попытку.
                    if (new.size <
                        ParentPassword.MIN_LENGTH
                    ) {
                        return@withLock ChangePasswordResult.TooShort
                    }
                    when (val check = verifyLocked(current)) {
                        PasswordCheck.Correct -> {
                            saveHash(new)
                            ChangePasswordResult.Changed
                        }

                        else -> ChangePasswordResult.Rejected(check)
                    }
                }
            }
        }

    override suspend fun lockoutRemaining(): Duration = mutex.withLock { lockout.remaining() }

    private suspend fun verifyLocked(password: CharArray): PasswordCheck {
        val remaining = lockout.remaining()
        if (remaining > Duration.ZERO) return PasswordCheck.LockedOut(remaining)

        return when (matchesStoredHash(password)) {
            true -> {
                lockout.reset()
                PasswordCheck.Correct
            }

            false -> {
                val failure = lockout.registerFailure()
                if (failure.lock > Duration.ZERO) {
                    PasswordCheck.LockedOut(failure.lock)
                } else {
                    PasswordCheck.Wrong(failure.attemptsBeforeLockout)
                }
            }

            null -> PasswordCheck.Unavailable
        }
    }

    /** @return null, если хеша нет или он не читается. */
    private suspend fun matchesStoredHash(password: CharArray): Boolean? {
        val record = try {
            store.readString(HASH_KEY)
        } catch (ignored: SecureStoreException) {
            null
        } ?: return null
        return try {
            withContext(hashingDispatcher) { hasher.verify(password, record) }
        } catch (ignored: IllegalArgumentException) {
            null
        }
    }

    private suspend fun saveHash(password: CharArray) {
        val record = withContext(hashingDispatcher) { hasher.hash(password) }
        store.writeString(HASH_KEY, record)
    }

    private inline fun <T> CharArray.wipeAfter(block: () -> T): T = try {
        block()
    } finally {
        fill('\u0000')
    }

    private companion object {
        const val HASH_KEY = "parent_password_hash"
    }
}
