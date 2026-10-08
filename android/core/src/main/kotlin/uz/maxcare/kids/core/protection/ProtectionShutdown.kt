package uz.maxcare.kids.core.protection

/**
 * Полное отключение защиты родителем: после него MaxCare Kids удаляется обычным способом.
 */
interface ProtectionShutdown {
    /**
     * Проверяет пароль родителя и, если он верный, снимает всё: запреты и режим Device Owner
     * или администратора устройства, а также постоянное уведомление.
     *
     * Неверный пароль тратит попытку, как любая проверка [ParentPassword.verify].
     * Массив с паролем затирается.
     */
    suspend fun disable(password: CharArray): DisableProtectionResult
}

sealed interface DisableProtectionResult {
    data object Disabled : DisableProtectionResult

    /** Защита и так не включена; пароль не проверялся, попытка не потрачена. */
    data object NotActive : DisableProtectionResult

    data class Rejected(val check: PasswordCheck) : DisableProtectionResult
}
