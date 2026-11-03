package uz.maxcare.kids.setup.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.maxcare.kids.core.filter.NetworkFilter
import uz.maxcare.kids.core.location.LocationScheduler
import uz.maxcare.kids.core.pairing.DevicePairing
import uz.maxcare.kids.core.pairing.PairingResult
import uz.maxcare.kids.core.pairing.UnpairResult
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
import uz.maxcare.kids.core.protection.TrustedSettingsWindow
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.storage.writeString
import uz.maxcare.kids.core.usage.AppUsageScheduler
import uz.maxcare.kids.setup.pairing.AppDataReset

/**
 * Экраны приложения ребёнка. Что осталось настроить, не хранится отдельно, а вычисляется заново
 * по фактическому состоянию (привязка, пароль, разрешения) при каждом открытии: если приложение
 * закрыли посреди настройки, оно продолжит с нужного места, а если ребёнок потом что-то выключил,
 * снова откроется экран «Разрешения» с этим пунктом.
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
    private val locationScheduler: LocationScheduler,
    private val appUsageScheduler: AppUsageScheduler,
    private val store: SecureStore,
    private val environment: SetupEnvironment,
    private val appDataReset: AppDataReset,
    private val trustedSettings: TrustedSettingsWindow
) : ViewModel() {
    private val _state = MutableStateFlow(KidsUiState())
    val state: StateFlow<KidsUiState> = _state.asStateFlow()

    private val _launches = Channel<SystemLaunch>(Channel.BUFFERED)

    /** Системные окна, которые открывает Activity: экран сам их открыть не может. */
    val launches: Flow<SystemLaunch> = _launches.receiveAsFlow()

    // Цепочка «Включить защиту»: идёт, пока пункты включаются, и останавливается на первом отказе.
    private var chainActive = false
    private val consented = mutableSetOf<SetupStep>()
    private var locationForegroundBefore = false
    private var notificationsDenied = false

    /**
     * Перед экраном Настроек для шага мастера: защита от удаления пропустит его (иначе на
     * экране есть название приложения, и он закрывается заглушкой).
     */
    fun beforeSettingsScreen() = trustedSettings.open()

    /** Пересчитать шаг: при открытии и при возврате из системных настроек. */
    fun refresh() {
        // Вернулись в приложение — всё дальнейшее в Настройках снова под защитой.
        trustedSettings.close()
        // На экранах отключения и восстановления пользователь вводит данные — не сбиваем его.
        val screen = _state.value.screen
        if (screen is KidsScreen.DisableProtection || screen is KidsScreen.RecoverPassword) return
        if (screen is KidsScreen.Disabled) return
        launchAction { showNextScreen() }
    }

    fun deviceAdminIntent(): Intent = adminActivation.activationIntent()

    /** Системный диалог согласия на VPN; null — согласие уже есть. */
    fun webFilterIntent(): Intent? = networkFilter.permissionIntent()

    /**
     * Кнопка экрана «Подключение»: согласие, привязка и пароль за одно нажатие.
     * Пароль проверяется до привязки: иначе телефон оказался бы связан, а пароль — не сохранён.
     * Если привязка прошла, а пароль нет, экран остаётся, но код вводить заново не нужно.
     */
    fun submitConnect(code: String, password: String, confirmation: String) = launchAction {
        val needsPassword = !parentPassword.isSet()
        val passwordProblem = when {
            !needsPassword -> null
            password != confirmation -> UiMessage.PasswordsDiffer
            password.length < ParentPassword.MIN_LENGTH -> UiMessage.PasswordTooShort
            else -> null
        }
        val problem = passwordProblem ?: connect(code, password, needsPassword)
        showNextScreen()
        problem?.let(::showMessage)
    }

    private suspend fun connect(
        code: String,
        password: String,
        needsPassword: Boolean
    ): UiMessage? {
        store.writeString(DISCLOSURE_ACCEPTED_KEY, FLAG_SET)
        val pairingResult = if (pairing.isPaired()) {
            PairingResult.AlreadyPaired
        } else {
            pairing.pair(
                code
            )
        }
        if (pairingResult != PairingResult.Paired && pairingResult != PairingResult.AlreadyPaired) {
            return pairingMessage(pairingResult)
        }
        val saved = !needsPassword ||
            parentPassword.setInitial(password.toCharArray()) != SetPasswordResult.TooShort
        return if (saved) null else UiMessage.PasswordTooShort
    }

    /** Кнопка экрана «Разрешения»: включать всё по очереди, пока не откажут или не закончится. */
    fun startPermissions() = launchAction {
        chainActive = true
        consented.clear()
        showNextScreen()
        advance()
    }

    /** Окно согласия: пользователь согласился — открываем системное окно. */
    fun acceptConsent() {
        val step = _state.value.consent ?: return
        consented += step
        _state.update { it.copy(consent = null) }
        launch(step)
    }

    /** Окно согласия закрыто без согласия: цепочка останавливается, пункт остаётся невключённым. */
    fun dismissConsent() {
        chainActive = false
        _state.update { it.copy(consent = null) }
    }

    /**
     * Вернулись из системного окна. Пункт сверяется с фактическим состоянием, а не с ответом
     * окна: разрешение могли выдать или отозвать в Настройках как угодно.
     */
    fun onSystemResult(step: SetupStep) = launchAction {
        trustedSettings.close()
        // Автозапуск проверить нельзя: подсказку считаем выполненной, когда родитель вернулся.
        if (step ==
            SetupStep.BACKGROUND_WORK
        ) {
            store.writeString(BACKGROUND_HINT_SHOWN_KEY, FLAG_SET)
        }
        showNextScreen()
        val done = isShownAsDone(step)
        if (done && step == SetupStep.LOCATION) scheduleLocation()
        if (done && step == SetupStep.USAGE_ACCESS) scheduleAppUsage()
        when {
            done -> if (chainActive) advance()

            // Геолокация двухступенчатая: после «При использовании» сразу просим «Разрешить всегда».
            step == SetupStep.LOCATION && chainActive && !locationForegroundBefore &&
                environment.locationForegroundAllowed() -> launch(step)

            else -> {
                chainActive = false
                if (step == SetupStep.NOTIFICATIONS) notificationsDenied = true
                showMessage(notDoneMessage(step))
            }
        }
    }

    /** Для автовозврата из Настроек: включено ли разрешение прямо сейчас. */
    fun isGranted(step: SetupStep): Boolean = when (step) {
        SetupStep.NOTIFICATIONS -> environment.notificationsAllowed()
        SetupStep.LOCATION -> environment.locationAllowed()
        SetupStep.USAGE_ACCESS -> environment.usageAccessAllowed()
        SetupStep.ACCESSIBILITY -> environment.accessibilityEnabled()
        SetupStep.DEVICE_ADMIN, SetupStep.WEB_FILTER, SetupStep.BACKGROUND_WORK -> false
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

    /**
     * Отвязать телефон от родителя. Только с экрана «Защита отключена», то есть сразу после ввода
     * пароля родителя, и только если защита действительно снята — иначе это был бы обход.
     * При успехе данные приложения стираются, процесс завершается.
     */
    fun unpair() = launchAction {
        if (_state.value.screen != KidsScreen.Disabled) return@launchAction
        if (protection.applyPolicies() != ProtectionMode.NONE) return@launchAction
        when (pairing.unpair()) {
            UnpairResult.Unpaired -> appDataReset.reset()
            UnpairResult.NoConnection -> showMessage(UiMessage.NoConnection)
            UnpairResult.InsecureConnection -> showMessage(UiMessage.InsecureConnection)
            UnpairResult.ServerError -> showMessage(UiMessage.ServerError)
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
        val paired = pairing.isPaired()
        val needsPassword = !parentPassword.isSet()
        if (!isFlagSet(DISCLOSURE_ACCEPTED_KEY) || !paired || needsPassword) {
            show(KidsScreen.Connect(paired, needsPassword))
            return
        }
        val steps = applicableSteps()
        // По порядку: проверка администратора заодно применяет политики и запускает уведомление.
        val done = steps.filterTo(mutableSetOf()) { isStepDone(it) }
        val screen = if (done.size == steps.size) {
            // Пункты с уже выданными разрешениями цепочка пропускает, и их результат не приходит —
            // например, после отвязки и новой привязки. Тогда фоновые задачи ставит конец настройки.
            chainActive = false
            scheduleLocation()
            scheduleAppUsage()
            KidsScreen.Home(protection.mode.value)
        } else {
            KidsScreen.Permissions(
                steps,
                done,
                canTurnOff = protection.mode.value != ProtectionMode.NONE
            )
        }
        show(screen)
    }

    private suspend fun isStepDone(step: SetupStep): Boolean = when (step) {
        SetupStep.DEVICE_ADMIN -> protection.applyPolicies() != ProtectionMode.NONE

        // В режиме Device Owner согласие на VPN выдаётся вместе с постоянным VPN.
        SetupStep.WEB_FILTER -> networkFilter.isPermitted()

        SetupStep.NOTIFICATIONS -> environment.notificationsAllowed()

        SetupStep.LOCATION -> environment.locationAllowed()

        SetupStep.USAGE_ACCESS -> environment.usageAccessAllowed()

        SetupStep.ACCESSIBILITY -> environment.accessibilityEnabled()

        SetupStep.BACKGROUND_WORK -> isFlagSet(BACKGROUND_HINT_SHOWN_KEY)
    }

    /** Следующий пункт цепочки: сначала окно согласия, если оно нужно, потом системное окно. */
    private fun advance() {
        val screen = _state.value.screen as? KidsScreen.Permissions
        if (screen == null) {
            chainActive = false
        } else if (screen.current in CONSENT_STEPS && screen.current !in consented) {
            _state.update { it.copy(consent = screen.current) }
        } else {
            launch(screen.current)
        }
    }

    private fun launch(step: SetupStep) {
        if (step == SetupStep.LOCATION) {
            locationForegroundBefore = environment.locationForegroundAllowed()
        }
        _launches.trySend(
            SystemLaunch(step, viaSettings = step == SetupStep.NOTIFICATIONS && notificationsDenied)
        )
    }

    private fun isShownAsDone(step: SetupStep): Boolean {
        val screen = _state.value.screen
        return screen !is KidsScreen.Permissions || step in screen.done
    }

    private fun notDoneMessage(step: SetupStep): UiMessage = when (step) {
        SetupStep.DEVICE_ADMIN -> UiMessage.AdminNotActivated

        SetupStep.WEB_FILTER -> UiMessage.FilterNotAllowed

        SetupStep.NOTIFICATIONS -> UiMessage.NotificationsDenied

        SetupStep.LOCATION, SetupStep.USAGE_ACCESS, SetupStep.ACCESSIBILITY,
        SetupStep.BACKGROUND_WORK -> UiMessage.StepNotDone
    }

    // Повторная постановка безопасна (уникальная задача WorkManager), но одной за запуск экрана хватает.
    private var locationScheduled = false
    private var appUsageScheduled = false

    private fun scheduleLocation() {
        if (locationScheduled) return
        locationScheduler.schedulePeriodic()
        locationScheduled = true
    }

    private fun scheduleAppUsage() {
        if (appUsageScheduled) return
        appUsageScheduler.schedulePeriodic()
        appUsageScheduled = true
    }

    private fun applicableSteps(): List<SetupStep> = SetupStep.entries.filter {
        it != SetupStep.BACKGROUND_WORK ||
            environment.needsBackgroundHint()
    }

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

        /** Перед этими пунктами — окно согласия: заметное раскрытие по политике Google Play. */
        val CONSENT_STEPS = setOf(
            SetupStep.WEB_FILTER,
            SetupStep.LOCATION,
            SetupStep.ACCESSIBILITY,
            SetupStep.BACKGROUND_WORK
        )
        const val SECONDS_PER_MINUTE = 60L
    }
}
