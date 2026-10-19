package uz.maxcare.kids.setup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import uz.maxcare.kids.core.protection.ProtectionMode
import uz.maxcare.kids.setup.R

@Composable
internal fun HomeScreen(screen: KidsScreen.Home, state: KidsUiState, viewModel: KidsViewModel) {
    val body = when (screen.mode) {
        ProtectionMode.DEVICE_OWNER -> stringResource(R.string.home_mode_owner)
        ProtectionMode.DEVICE_ADMIN, ProtectionMode.NONE -> stringResource(R.string.home_mode_admin)
    }
    ScreenLayout(title = stringResource(R.string.home_title), body = body, state = state) {
        SecondaryButton(
            stringResource(R.string.home_disable),
            !state.busy,
            viewModel::openDisableProtection
        )
    }
}

@Composable
internal fun DisableProtectionScreen(state: KidsUiState, viewModel: KidsViewModel) {
    BackHandler(onBack = viewModel::back)
    var password by remember { mutableStateOf("") }
    ScreenLayout(
        title = stringResource(R.string.disable_title),
        body = stringResource(R.string.disable_body),
        state = state
    ) {
        PasswordField(password, stringResource(R.string.password_label)) { password = it }
        PrimaryButton(
            stringResource(R.string.disable_submit),
            !state.busy && password.isNotEmpty()
        ) {
            viewModel.submitDisable(password)
            password = ""
        }
        SecondaryButton(
            stringResource(R.string.forgot_password),
            !state.busy,
            viewModel::openPasswordRecovery
        )
        SecondaryButton(stringResource(R.string.back), !state.busy, viewModel::back)
    }
}

@Composable
internal fun RecoverPasswordScreen(state: KidsUiState, viewModel: KidsViewModel) {
    BackHandler(onBack = viewModel::back)
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    ScreenLayout(
        title = stringResource(R.string.recovery_title),
        body = stringResource(R.string.recovery_body),
        state = state
    ) {
        CodeField(code, stringResource(R.string.recovery_code_label)) { code = it }
        PasswordField(password, stringResource(R.string.recovery_new_password)) { password = it }
        PasswordField(confirmation, stringResource(R.string.password_confirm_label)) {
            confirmation =
                it
        }
        PrimaryButton(
            stringResource(R.string.recovery_submit),
            !state.busy && code.isNotBlank() && password.isNotEmpty()
        ) {
            viewModel.submitRecovery(code, password, confirmation)
        }
        SecondaryButton(stringResource(R.string.back), !state.busy, viewModel::back)
    }
}

@Composable
internal fun DisabledScreen(state: KidsUiState, viewModel: KidsViewModel) {
    var confirming by remember { mutableStateOf(false) }
    ScreenLayout(
        title = stringResource(R.string.disabled_title),
        body = stringResource(R.string.disabled_body),
        state = state
    ) {
        SecondaryButton(stringResource(R.string.unpair), !state.busy) { confirming = true }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.unpair_confirm_title)) },
            text = { Text(stringResource(R.string.unpair_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    viewModel.unpair()
                }) { Text(stringResource(R.string.unpair_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.unpair_cancel))
                }
            }
        )
    }
}
