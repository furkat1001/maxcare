package uz.maxcare.kids.setup.ui

import uz.maxcare.kids.core.protection.ProtectionMode

/** Шаги первой настройки в порядке прохождения. */
enum class SetupStep {
    DISCLOSURE,
    PAIRING,
    PASSWORD,
    DEVICE_ADMIN,
    WEB_FILTER,
    NOTIFICATIONS,
    LOCATION,

    /** Только на прошивках, которые агрессивно закрывают фоновые приложения. */
    BACKGROUND_WORK
}

sealed interface KidsScreen {
    data object Loading : KidsScreen

    /**
     * @param canTurnOff защита уже работает (администратор активен): родитель может отключить её
     *   прямо с шага, который просит что-то включить обратно, — например, после того как ребёнок
     *   удалил VPN-профиль или выключил уведомления.
     */
    data class Setup(
        val step: SetupStep,
        val number: Int,
        val total: Int,
        val canTurnOff: Boolean = false
    ) : KidsScreen

    data class Home(val mode: ProtectionMode) : KidsScreen

    data object DisableProtection : KidsScreen

    data object RecoverPassword : KidsScreen

    data object Disabled : KidsScreen
}

/** Сообщения пользователю; текст на нужном языке подставляет экран. */
sealed interface UiMessage {
    data object InvalidCodeFormat : UiMessage
    data object InvalidCode : UiMessage
    data object TooManyAttempts : UiMessage
    data object NoConnection : UiMessage
    data object InsecureConnection : UiMessage
    data object ServerError : UiMessage
    data object PasswordTooShort : UiMessage
    data object PasswordsDiffer : UiMessage
    data class WrongPassword(val attemptsLeft: Int) : UiMessage
    data class LockedOut(val minutes: Long) : UiMessage
    data object StorageDamaged : UiMessage
    data object AdminNotActivated : UiMessage
    data object NotificationsDenied : UiMessage
    data object FilterNotAllowed : UiMessage
    data class WrongRecoveryCode(val attemptsLeft: Int) : UiMessage
    data object NotPaired : UiMessage
    data object PasswordReset : UiMessage
}

data class KidsUiState(
    val screen: KidsScreen = KidsScreen.Loading,
    val busy: Boolean = false,
    val message: UiMessage? = null
)
