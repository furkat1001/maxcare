package uz.maxcare.kids.setup.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import uz.maxcare.kids.core.R as CoreR
import uz.maxcare.kids.setup.R

/**
 * Общий каркас экрана: логотип, заголовок, текст, поля, сообщение, кнопки.
 * Прокручивается и не прячется под клавиатурой — на маленьких телефонах текст длинный.
 */
@Composable
internal fun ScreenLayout(
    title: String,
    body: String,
    state: KidsUiState,
    modifier: Modifier = Modifier,
    caption: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(CoreR.drawable.ic_maxcare_logo),
            contentDescription = null,
            modifier = Modifier.size(56.dp)
        )
        caption?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        content()
        state.message?.let { MessageText(it) }
        if (state.busy) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
internal fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text)
    }
}

@Composable
internal fun SecondaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text)
    }
}

@Composable
internal fun PasswordField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun CodeField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.take(MAX_CODE_INPUT)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MessageText(message: UiMessage) {
    val positive = message is UiMessage.PasswordReset
    Text(
        text = messageText(message),
        color = with(MaterialTheme.colorScheme) { if (positive) primary else error },
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun messageText(message: UiMessage): String = when (message) {
    UiMessage.InvalidCodeFormat -> stringResource(R.string.error_code_format)

    UiMessage.InvalidCode -> stringResource(R.string.error_code_invalid)

    UiMessage.TooManyAttempts -> stringResource(R.string.error_too_many_attempts)

    UiMessage.NoConnection -> stringResource(R.string.error_no_connection)

    UiMessage.InsecureConnection -> stringResource(R.string.error_insecure_connection)

    UiMessage.ServerError -> stringResource(R.string.error_server)

    UiMessage.PasswordTooShort -> stringResource(R.string.error_password_short)

    UiMessage.PasswordsDiffer -> stringResource(R.string.error_passwords_differ)

    is UiMessage.WrongPassword -> stringResource(
        R.string.error_wrong_password,
        message.attemptsLeft
    )

    is UiMessage.LockedOut -> stringResource(R.string.error_locked_out, message.minutes)

    UiMessage.StorageDamaged -> stringResource(R.string.error_storage)

    UiMessage.AdminNotActivated -> stringResource(R.string.error_admin_not_activated)

    UiMessage.NotificationsDenied -> stringResource(R.string.error_notifications_denied)

    UiMessage.FilterNotAllowed -> stringResource(R.string.error_filter_not_allowed)

    is UiMessage.WrongRecoveryCode -> stringResource(
        R.string.error_recovery_code_invalid,
        message.attemptsLeft
    )

    UiMessage.NotPaired -> stringResource(R.string.error_not_paired)

    UiMessage.PasswordReset -> stringResource(R.string.message_password_reset)
}

// С запасом на пробелы и дефис: «1234 5678», «1234-5678».
private const val MAX_CODE_INPUT = 12
