package uz.maxcare.kids.setup.ui

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
    when (screen.step) {
        SetupStep.DISCLOSURE -> DisclosureStep(state, caption, viewModel::acceptDisclosure)

        SetupStep.PAIRING -> PairingStep(state, caption, viewModel::submitPairingCode)

        SetupStep.PASSWORD -> PasswordStep(state, caption, viewModel::submitNewPassword)

        SetupStep.DEVICE_ADMIN -> ScreenLayout(
            title = stringResource(R.string.admin_title),
            body = stringResource(R.string.admin_body),
            state = state,
            caption = caption
        ) {
            PrimaryButton(
                stringResource(R.string.admin_submit),
                !state.busy,
                system.activateDeviceAdmin
            )
        }

        SetupStep.NOTIFICATIONS -> ScreenLayout(
            title = stringResource(R.string.notifications_title),
            body = stringResource(R.string.notifications_body),
            state = state,
            caption = caption
        ) {
            PrimaryButton(
                stringResource(R.string.notifications_allow),
                !state.busy,
                system.requestNotifications
            )
            SecondaryButton(
                stringResource(R.string.open_settings),
                !state.busy,
                system.openNotificationSettings
            )
        }

        SetupStep.BACKGROUND_WORK -> ScreenLayout(
            title = stringResource(R.string.background_title),
            body = stringResource(R.string.background_body),
            state = state,
            caption = caption
        ) {
            PrimaryButton(
                stringResource(R.string.open_settings),
                !state.busy,
                system.openAppSettings
            )
            SecondaryButton(
                stringResource(R.string.background_done),
                !state.busy,
                viewModel::confirmBackgroundWork
            )
        }
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
