package uz.maxcare.kids.core.protection

import kotlin.time.Duration

/**
 * Родитель забыл пароль: получает код восстановления в своём аккаунте на сервере
 * и вводит его на телефоне ребёнка вместе с новым паролем.
 *
 * Работает без сети — код проверяется по секрету, полученному при привязке, — поэтому
 * выключенный интернет не мешает родителю и не помогает ребёнку. Нужно правильное время
 * на телефоне: код меняется каждые 5 минут.
 */
interface PasswordRecovery {
    /** Массив с новым паролем затирается. */
    suspend fun reset(code: String, newPassword: CharArray): RecoveryResult
}

sealed interface RecoveryResult {
    data object Reset : RecoveryResult

    /** Не 8 цифр — попытка не засчитана. */
    data object InvalidFormat : RecoveryResult

    /** Новый пароль короче [ParentPassword.MIN_LENGTH] — код не проверялся, попытка не засчитана. */
    data object TooShort : RecoveryResult

    data class InvalidCode(val attemptsBeforeLockout: Int) : RecoveryResult

    data class LockedOut(val remaining: Duration) : RecoveryResult

    /** Телефон не привязан к родителю — секрета для проверки нет. */
    data object NotPaired : RecoveryResult

    /** Хранилище повреждено: секрет не читается. */
    data object Unavailable : RecoveryResult
}
