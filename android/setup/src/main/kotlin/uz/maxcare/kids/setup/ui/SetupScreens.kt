package uz.maxcare.kids.setup.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import uz.maxcare.kids.setup.R

@Composable
internal fun SetupStepScreen(
    screen: KidsScreen.Setup,
    state: KidsUiState,
    viewModel: KidsViewModel,
    system: SystemActions
) {
    val caption = stringResource(R.string.setup_step_counter, screen.number, screen.total)
    val turnOff = viewModel::openDisableProtection.takeIf { screen.canTurnOff }
    when (screen.step) {
        SetupStep.DISCLOSURE -> DisclosureStep(state, caption, viewModel::acceptDisclosure)

        SetupStep.PAIRING -> PairingStep(state, caption, viewModel::submitPairingCode)

        SetupStep.PASSWORD -> PasswordStep(state, caption, viewModel::submitNewPassword)

        SetupStep.DEVICE_ADMIN -> ActionStep(
            R.string.admin_title,
            R.string.admin_body,
            state,
            caption,
            R.string.admin_submit to system.activateDeviceAdmin
        )

        // Отдельное раскрытие о VPN: политика Google Play не разрешает объединять его с другими.
        SetupStep.WEB_FILTER -> ActionStep(
            R.string.filter_title,
            R.string.filter_body,
            state,
            caption,
            R.string.filter_submit to system.enableWebFilter,
            turnOff = turnOff
        )

        SetupStep.NOTIFICATIONS -> ActionStep(
            R.string.notifications_title,
            R.string.notifications_body,
            state,
            caption,
            R.string.notifications_allow to system.requestNotifications,
            R.string.open_settings to system.openNotificationSettings,
            turnOff = turnOff
        )

        // Раскрытие о фоновой геолокации (политика Google Play): зачем и как «Разрешить всегда».
        SetupStep.LOCATION -> ActionStep(
            R.string.location_title,
            R.string.location_body,
            state,
            caption,
            R.string.location_allow to system.requestLocation,
            R.string.open_settings to system.openAppSettings,
            turnOff = turnOff
        )

        SetupStep.BACKGROUND_WORK -> ActionStep(
            R.string.background_title,
            R.string.background_body,
            state,
            caption,
            R.string.open_settings to system.openAppSettings,
            R.string.background_done to viewModel::confirmBackgroundWork,
            turnOff = turnOff
        )
    }
}

/** Шаг «заголовок, текст, кнопка и, возможно, вторая кнопка». */
@Composable
@Suppress("LongParameterList") // Параметры шага — все обязательные, группировать их не во что
private fun ActionStep(
    @StringRes title: Int,
    @StringRes body: Int,
    state: KidsUiState,
    caption: String,
    primary: Pair<Int, () -> Unit>,
    secondary: Pair<Int, () -> Unit>? = null,
    turnOff: (() -> Unit)? = null
) {
    ScreenLayout(
        title = stringResource(title),
        body = stringResource(body),
        state = state,
        caption = caption
    ) {
        PrimaryButton(stringResource(primary.first), !state.busy, primary.second)
        secondary?.let { (text, action) ->
            SecondaryButton(stringResource(text), !state.busy, action)
        }
        turnOff?.let { SecondaryButton(stringResource(R.string.home_disable), !state.busy, it) }
    }
}

@Composable
private fun DisclosureStep(state: KidsUiState, caption: String, onAccept: () -> Unit) {
    ScreenLayout(
        title = stringResource(R.string.disclosure_title),
        body = stringResource(R.string.disclosure_body),
        state = state,
        caption = caption
    ) {
        PrimaryButton(stringResource(R.string.disclosure_accept), !state.busy, onAccept)
    }
}

@Composable
private fun PairingStep(state: KidsUiState, caption: String, onSubmit: (String) -> Unit) {
    // Код привязки не секретен после использования, его можно пережить поворот экрана.
    var code by rememberSaveable { mutableStateOf("") }
    ScreenLayout(
        title = stringResource(R.string.pairing_title),
        body = stringResource(R.string.pairing_body),
        state = state,
        caption = caption
    ) {
        CodeField(code, stringResource(R.string.pairing_code_label)) { code = it }
        PrimaryButton(stringResource(R.string.pairing_submit), !state.busy && code.isNotBlank()) {
            onSubmit(code)
        }
    }
}

@Composable
private fun PasswordStep(state: KidsUiState, caption: String, onSubmit: (String, String) -> Unit) {
    // remember, а не rememberSaveable: пароль не должен попасть в сохранённое состояние на диске.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    ScreenLayout(
        title = stringResource(R.string.password_title),
        body = stringResource(R.string.password_body),
        state = state,
        caption = caption
    ) {
        PasswordField(password, stringResource(R.string.password_label)) { password = it }
        PasswordField(confirmation, stringResource(R.string.password_confirm_label)) {
            confirmation =
                it
        }
        PrimaryButton(
            stringResource(R.string.password_submit),
            !state.busy && password.isNotEmpty() && confirmation.isNotEmpty()
        ) {
            onSubmit(password, confirmation)
        }
    }
}
