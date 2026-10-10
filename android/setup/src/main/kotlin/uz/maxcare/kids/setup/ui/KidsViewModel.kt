package uz.maxcare.kids.setup.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.maxcare.kids.core.filter.NetworkFilter
import uz.maxcare.kids.core.pairing.DevicePairing
import uz.maxcare.kids.core.pairing.PairingResult
import uz.maxcare.kids.core.protection.DeviceAdminActivation
import uz.maxcare.kids.core.protection.DeviceProtection
import uz.maxcare.kids.core.protection.DisableProtectionResult
import uz.maxcare.kids.core.protection.ParentPassword
import uz.maxcare.kids.core.protection.PasswordCheck
import uz.maxcare.kids.core.protection.PasswordRecovery
import uz.maxcare.kids.core.protection.ProtectionMode
import uz.maxcare.kids.core.protection.ProtectionShutdown
import uz.maxcare.kids.core.protection.RecoveryResult
import uz.maxcare.kids.core.protection.SetPasswordResult
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.storage.writeString

/**
 * Экраны приложения ребёнка. Шаг настройки не хранится отдельно, а вычисляется заново по
 * фактическому состоянию (привязка, пароль, администратор, уведомления) при каждом открытии:
 * если приложение закрыли посреди настройки, оно продолжит с нужного места.
 *
 * Пароли приходят из полей ввода строками: Compose хранит текст поля как String, затереть её
 * нельзя. Дальше — массив символов, который модули защиты затирают после проверки.
 */
@Suppress("TooManyFunctions") // Каждое действие экрана — отдельная функция
@HiltViewModel
class KidsViewModel @Inject internal constructor(
    private val pairing: DevicePairing,
    private val parentPassword: ParentPassword,
    private val protection: DeviceProtection,
    private val shutdown: ProtectionShutdown,
    private val recovery: PasswordRecovery,
    private val adminActivation: DeviceAdminActivation,
    private val networkFilter: NetworkFilter,
    private val store: SecureStore,
    private val environment: SetupEnvironment
) : ViewModel() {
    private val _state = MutableStateFlow(KidsUiState())
    val state: StateFlow<KidsUiState> = _state.asStateFlow()

    /** Пересчитать шаг: при открытии и при возврате из системных настроек. */
    fun refresh() {
        // На экранах отключения и восстановления пользователь вводит данные — не сбиваем его.
        val screen = _state.value.screen
        if (screen is KidsScreen.DisableProtection || screen is KidsScreen.RecoverPassword) return
        if (screen is KidsScreen.Disabled) return
        launchAction { showNextScreen() }
    }

    fun deviceAdminIntent(): Intent = adminActivation.activationIntent()

    /** Системный диалог согласия на VPN; null — согласие уже есть. */
    fun webFilterIntent(): Intent? = networkFilter.permissionIntent()

    fun acceptDisclosure() = launchAction {
        store.writeString(DISCLOSURE_ACCEPTED_KEY, FLAG_SET)
        showNextScreen()
    }

    fun submitPairingCode(code: String) = launchAction {
        when (val result = pairing.pair(code)) {
            PairingResult.Paired, PairingResult.AlreadyPaired -> showNextScreen()
            else -> showMessage(pairingMessage(result))
        }
    }

    fun submitNewPassword(password: String, confirmation: String) = launchAction {
        if (password != confirmation) {
            showMessage(UiMessage.PasswordsDiffer)
        } else {
            when (parentPassword.setInitial(password.toCharArray())) {
                SetPasswordResult.TooShort -> showMessage(UiMessage.PasswordTooShort)
                SetPasswordResult.Saved, SetPasswordResult.AlreadySet -> showNextScreen()
            }
        }
    }

    fun onDeviceAdminResult() = launchAction {
        showNextScreen()
        if (currentStep() == SetupStep.DEVICE_ADMIN) showMessage(UiMessage.AdminNotActivated)
    }

    fun onWebFilterResult() = launchAction {
        showNextScreen()
        if (currentStep() == SetupStep.WEB_FILTER) showMessage(UiMessage.FilterNotAllowed)
    }

    fun onNotificationPermissionResult() = launchAction {
        showNextScreen()
        if (currentStep() == SetupStep.NOTIFICATIONS) showMessage(UiMessage.NotificationsDenied)
    }

    fun confirmBackgroundWork() = launchAction {
        store.writeString(BACKGROUND_HINT_SHOWN_KEY, FLAG_SET)
        showNextScreen()
    }

    fun openDisableProtection() = show(KidsScreen.DisableProtection)

    fun openPasswordRecovery() = show(KidsScreen.RecoverPassword)

    fun back() = launchAction { showNextScreen() }

    fun submitDisable(password: String) = launchAction {
        when (val result = shutdown.disable(password.toCharArray())) {
            DisableProtectionResult.Disabled -> show(KidsScreen.Disabled)
            DisableProtectionResult.NotActive -> showNextScreen()
            is DisableProtectionResult.Rejected -> showMessage(passwordMessage(result.check))
        }
    }

    fun submitRecovery(code: String, password: String, confirmation: String) = launchAction {
        if (password != confirmation) {
            showMessage(UiMessage.PasswordsDiffer)
        } else {
            when (val result = recovery.reset(code, password.toCharArray())) {
                RecoveryResult.Reset -> {
                    showNextScreen()
                    showMessage(UiMessage.PasswordReset)
                }

                else -> showMessage(recoveryMessage(result))
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private suspend fun showNextScreen() {
        val step = firstIncompleteStep()
        val screen = if (step == null) {
            KidsScreen.Home(protection.mode.value)
        } else {
            val steps = applicableSteps()
            KidsScreen.Setup(
                step,
                number = steps.indexOf(step) + 1,
                total = steps.size,
                canTurnOff = protection.mode.value != ProtectionMode.NONE
            )
        }
        show(screen)
    }

    private suspend fun firstIncompleteStep(): SetupStep? = when {
        !isFlagSet(DISCLOSURE_ACCEPTED_KEY) -> SetupStep.DISCLOSURE

        !pairing.isPaired() -> SetupStep.PAIRING

        !parentPassword.isSet() -> SetupStep.PASSWORD

        // Заодно применяет политики и запускает постоянное уведомление.
        protection.applyPolicies() == ProtectionMode.NONE -> SetupStep.DEVICE_ADMIN

        // В режиме Device Owner согласие на VPN выдаётся вместе с постоянным VPN — шаг пропускается.
        !networkFilter.isPermitted() -> SetupStep.WEB_FILTER

        !environment.notificationsAllowed() -> SetupStep.NOTIFICATIONS

        environment.needsBackgroundHint() && !isFlagSet(BACKGROUND_HINT_SHOWN_KEY) ->
            SetupStep.BACKGROUND_WORK

        else -> null
    }

    private fun applicableSteps(): List<SetupStep> = SetupStep.entries.filter {
        it != SetupStep.BACKGROUND_WORK ||
            environment.needsBackgroundHint()
    }

    private fun currentStep(): SetupStep? = (_state.value.screen as? KidsScreen.Setup)?.step

    private suspend fun isFlagSet(key: String): Boolean = try {
        store.readString(key) == FLAG_SET
    } catch (ignored: SecureStoreException) {
        // Флаги шагов не защищают ничего: повторный показ шага безопасен.
        false
    }

    private fun show(screen: KidsScreen) = _state.update {
        it.copy(screen = screen, message = null)
    }

    private fun showMessage(message: UiMessage) = _state.update { it.copy(message = message) }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                block()
            } catch (ignored: SecureStoreException) {
                showMessage(UiMessage.StorageDamaged)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun pairingMessage(result: PairingResult): UiMessage = when (result) {
        PairingResult.InvalidFormat -> UiMessage.InvalidCodeFormat

        PairingResult.InvalidCode -> UiMessage.InvalidCode

        PairingResult.TooManyAttempts -> UiMessage.TooManyAttempts

        PairingResult.NoConnection -> UiMessage.NoConnection

        PairingResult.InsecureConnection -> UiMessage.InsecureConnection

        PairingResult.ServerError,
        PairingResult.Paired,
        PairingResult.AlreadyPaired -> UiMessage.ServerError
    }

    private fun passwordMessage(check: PasswordCheck): UiMessage = when (check) {
        is PasswordCheck.Wrong -> UiMessage.WrongPassword(check.attemptsBeforeLockout)
        is PasswordCheck.LockedOut -> UiMessage.LockedOut(check.remaining.toWholeMinutesRoundedUp())
        PasswordCheck.Unavailable, PasswordCheck.Correct -> UiMessage.StorageDamaged
    }

    private fun recoveryMessage(result: RecoveryResult): UiMessage = when (result) {
        RecoveryResult.InvalidFormat -> UiMessage.InvalidCodeFormat

        RecoveryResult.TooShort -> UiMessage.PasswordTooShort

        is RecoveryResult.InvalidCode -> UiMessage.WrongRecoveryCode(result.attemptsBeforeLockout)

        is RecoveryResult.LockedOut -> UiMessage.LockedOut(
            result.remaining.toWholeMinutesRoundedUp()
        )

        RecoveryResult.NotPaired -> UiMessage.NotPaired

        RecoveryResult.Unavailable, RecoveryResult.Reset -> UiMessage.StorageDamaged
    }

    // «Осталось 0 минут» при 30 секундах ожидания сбивает с толку — округляем вверх.
    private fun Duration.toWholeMinutesRoundedUp(): Long =
        (inWholeSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE

    private companion object {
        const val DISCLOSURE_ACCEPTED_KEY = "setup_disclosure_accepted"
        const val BACKGROUND_HINT_SHOWN_KEY = "setup_background_hint_shown"
        const val FLAG_SET = "1"
        const val SECONDS_PER_MINUTE = 60L
    }
}
