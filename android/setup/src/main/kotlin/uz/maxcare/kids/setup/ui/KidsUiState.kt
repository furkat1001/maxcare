package uz.maxcare.kids.setup.ui

import uz.maxcare.kids.core.protection.ProtectionMode

/** Разрешения, которые включает экран «Разрешения», в порядке включения. */
enum class SetupStep {
    DEVICE_ADMIN,
    WEB_FILTER,
    NOTIFICATIONS,
    LOCATION,
    USAGE_ACCESS,

    /**
     * Служба доступности: фильтр текста и картинок, ограничения времени, защита от удаления.
     * Предпоследняя: пока служба включена, защита от удаления закрывает экраны Настроек
     * с названием приложения — остальные разрешения включаются раньше.
     */
    ACCESSIBILITY,

    /** Только на прошивках, которые агрессивно закрывают фоновые приложения. */
    BACKGROUND_WORK
}

sealed interface KidsScreen {
    data object Loading : KidsScreen

    /**
     * Знакомство, код привязки и пароль родителя на одном экране.
     * @param paired код уже введён: остался только пароль.
     * @param needsPassword пароль родителя ещё не задан.
     */
    data class Connect(val paired: Boolean, val needsPassword: Boolean = true) : KidsScreen

    /**
     * Список разрешений с отметками и одна кнопка, которая включает их по очереди.
     * @param canTurnOff защита уже работает (администратор активен): родитель может отключить её
     *   прямо отсюда — например, после того как ребёнок удалил VPN-профиль или выключил уведомления.
     */
    data class Permissions(
        val steps: List<SetupStep>,
        val done: Set<SetupStep>,
        val canTurnOff: Boolean = false
    ) : KidsScreen {
        /** Следующее невключённое разрешение; экран показывается, только пока такое есть. */
        val current: SetupStep get() = steps.first { it !in done }
    }

    data class Home(val mode: ProtectionMode) : KidsScreen

    data object DisableProtection : KidsScreen

    data object RecoverPassword : KidsScreen

    data object Disabled : KidsScreen
}

/** Системное окно, которое должна открыть Activity. */
data class SystemLaunch(
    val step: SetupStep,
    /** Уведомления уже запрещали: системный диалог больше не появится, открываем Настройки. */
    val viaSettings: Boolean = false
)

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
    data object StepNotDone : UiMessage
    data class WrongRecoveryCode(val attemptsLeft: Int) : UiMessage
    data object NotPaired : UiMessage
    data object PasswordReset : UiMessage
}

/**
 * @param consent окно согласия перед разрешением (политика Google Play о заметном раскрытии);
 *   null — окна нет.
 */
data class KidsUiState(
    val screen: KidsScreen = KidsScreen.Loading,
    val busy: Boolean = false,
    val message: UiMessage? = null,
    val consent: SetupStep? = null
)
