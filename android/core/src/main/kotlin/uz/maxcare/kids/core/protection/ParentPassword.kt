package uz.maxcare.kids.core.protection

import kotlin.time.Duration

/**
 * Пароль родителя: нужен, чтобы отключить защиту или изменить настройки на телефоне ребёнка.
 * Хранится только хеш; проверка работает без сети.
 *
 * Все методы, принимающие пароль, затирают переданный массив после использования —
 * поэтому пароль передаётся как [CharArray], а не [String].
 *
 * [setInitial] и [change] бросают [uz.maxcare.kids.core.storage.SecureStoreException],
 * если новый хеш не удалось записать.
 */
interface ParentPassword {
    /** true и тогда, когда хеш есть, но прочитать его нельзя: повреждённый пароль не считается «не заданным». */
    suspend fun isSet(): Boolean

    /** Первый ввод при настройке. Если пароль уже задан, его можно только сменить через [change]. */
    suspend fun setInitial(password: CharArray): SetPasswordResult

    suspend fun verify(password: CharArray): PasswordCheck

    suspend fun change(current: CharArray, new: CharArray): ChangePasswordResult

    /** Сколько ещё ждать до следующей попытки; [Duration.ZERO], если ввод не заблокирован. */
    suspend fun lockoutRemaining(): Duration

    companion object {
        const val MIN_LENGTH = 6
    }
}

sealed interface PasswordCheck {
    data object Correct : PasswordCheck

    /** @param attemptsBeforeLockout сколько ещё неверных попыток до блокировки ввода. */
    data class Wrong(val attemptsBeforeLockout: Int) : PasswordCheck

    /** Ввод заблокирован после серии ошибок; пароль в это время даже не проверяется. */
    data class LockedOut(val remaining: Duration) : PasswordCheck

    /** Хранилище повреждено, пароль проверить нельзя. Выход — код восстановления от родителя. */
    data object Unavailable : PasswordCheck
}

sealed interface SetPasswordResult {
    data object Saved : SetPasswordResult
    data object TooShort : SetPasswordResult
    data object AlreadySet : SetPasswordResult
}

sealed interface ChangePasswordResult {
    data object Changed : ChangePasswordResult
    data object TooShort : ChangePasswordResult

    /** Текущий пароль не принят: неверный, ввод заблокирован или хранилище повреждено. */
    data class Rejected(val check: PasswordCheck) : ChangePasswordResult
}
