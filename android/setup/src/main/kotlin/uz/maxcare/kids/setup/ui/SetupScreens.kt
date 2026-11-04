package uz.maxcare.kids.setup.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uz.maxcare.kids.setup.R

/**
 * Знакомство, код привязки и пароль родителя. Короткое раскрытие видно сразу, полное — по ссылке
 * «Подробнее о данных»; согласие даёт кнопка «Я родитель, продолжить».
 */
@Composable
internal fun ConnectScreen(
    screen: KidsScreen.Connect,
    state: KidsUiState,
    viewModel: KidsViewModel
) {
    // Код привязки не секретен после использования, его можно пережить поворот экрана.
    var code by rememberSaveable { mutableStateOf("") }
    // remember, а не rememberSaveable: пароль не должен попасть в сохранённое состояние на диске.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf(false) }
    val ready = (screen.paired || code.isNotBlank()) &&
        (!screen.needsPassword || (password.isNotEmpty() && confirmation.isNotEmpty()))

    ScreenLayout(
        title = stringResource(R.string.connect_title),
        body = "",
        state = state
    ) {
        DisclosureSummary(onDetails = { details = true })
        if (screen.paired) {
            Text(
                stringResource(R.string.connect_paired),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
        } else {
            Text(stringResource(R.string.pairing_body), style = MaterialTheme.typography.bodyMedium)
            CodeField(code, stringResource(R.string.pairing_code_label)) { code = it }
        }
        if (screen.needsPassword) {
            Text(
                stringResource(R.string.password_body),
                style = MaterialTheme.typography.bodyMedium
            )
            PasswordField(password, stringResource(R.string.password_label)) { password = it }
            PasswordField(confirmation, stringResource(R.string.password_confirm_label)) {
                confirmation = it
            }
        }
        PrimaryButton(stringResource(R.string.disclosure_accept), !state.busy && ready) {
            viewModel.submitConnect(code, password, confirmation)
        }
    }
    if (details) {
        TextDialog(
            title = stringResource(R.string.disclosure_title),
            body = stringResource(R.string.disclosure_body),
            confirm = stringResource(R.string.close),
            onConfirm = { details = false },
            onDismiss = { details = false }
        )
    }
}

@Composable
private fun DisclosureSummary(onDetails: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.connect_summary),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.connect_not_doing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onDetails, modifier = Modifier.padding(0.dp)) {
                Text(stringResource(R.string.connect_more))
            }
        }
    }
}

/** Список разрешений с отметками; кнопка включает их по очереди. */
@Composable
internal fun PermissionsScreen(
    screen: KidsScreen.Permissions,
    state: KidsUiState,
    viewModel: KidsViewModel
) {
    ScreenLayout(
        title = stringResource(R.string.permissions_title),
        body = stringResource(R.string.permissions_body),
        state = state
    ) {
        Text(
            stringResource(R.string.permissions_progress, screen.done.size, screen.steps.size),
            style = MaterialTheme.typography.labelLarge
        )
        LinearProgressIndicator(
            progress = { screen.done.size.toFloat() / screen.steps.size },
            modifier = Modifier.fillMaxWidth()
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            screen.steps.forEach { step ->
                PermissionRow(step, done = step in screen.done, current = step == screen.current)
            }
        }
        val start = if (screen.done.isEmpty()) {
            R.string.permissions_start
        } else {
            R.string.permissions_continue
        }
        PrimaryButton(stringResource(start), !state.busy, viewModel::startPermissions)
        if (screen.canTurnOff) {
            SecondaryButton(
                stringResource(R.string.home_disable),
                !state.busy,
                viewModel::openDisableProtection
            )
        }
    }
    state.consent?.let { step ->
        val texts = consentTexts(step)
        TextDialog(
            title = stringResource(texts.title),
            body = stringResource(texts.body),
            confirm = stringResource(texts.confirm),
            onConfirm = viewModel::acceptConsent,
            onDismiss = viewModel::dismissConsent,
            dismiss = stringResource(R.string.consent_later)
        )
    }
}

@Composable
private fun PermissionRow(step: SetupStep, done: Boolean, current: Boolean) {
    val texts = rowTexts(step)
    val title = stringResource(texts.title)
    val hint = stringResource(texts.hint)
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (current) colors.primaryContainer else colors.surface,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val doneLabel = stringResource(R.string.permissions_done)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .background(if (done) colors.primary else colors.surfaceVariant, CircleShape)
                .clearAndSetSemantics { if (done) contentDescription = doneLabel }
        ) {
            if (done) {
                Text(
                    "✓",
                    color = colors.onPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/** Диалог с длинным текстом: текст прокручивается, кнопки остаются на месте. */
@Composable
@Suppress("LongParameterList") // Тексты и действия диалога — все обязательные
private fun TextDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = dismiss?.let { { TextButton(onClick = onDismiss) { Text(it) } } }
    )
}

private class RowTexts(@StringRes val title: Int, @StringRes val hint: Int)

private fun rowTexts(step: SetupStep): RowTexts = when (step) {
    SetupStep.DEVICE_ADMIN -> RowTexts(R.string.perm_admin_title, R.string.perm_admin_hint)

    SetupStep.WEB_FILTER -> RowTexts(R.string.filter_title, R.string.perm_filter_hint)

    SetupStep.NOTIFICATIONS -> RowTexts(
        R.string.perm_notifications_title,
        R.string.perm_notifications_hint
    )

    SetupStep.LOCATION -> RowTexts(R.string.location_title, R.string.perm_location_hint)

    SetupStep.USAGE_ACCESS -> RowTexts(R.string.perm_usage_title, R.string.perm_usage_hint)

    SetupStep.ACCESSIBILITY -> RowTexts(
        R.string.accessibility_title,
        R.string.perm_accessibility_hint
    )

    SetupStep.BACKGROUND_WORK -> RowTexts(R.string.background_title, R.string.perm_background_hint)
}

private class ConsentTexts(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val confirm: Int
)

// Заметные раскрытия (политика Google Play): тексты те же, что были на отдельных шагах мастера.
private fun consentTexts(step: SetupStep): ConsentTexts = when (step) {
    SetupStep.WEB_FILTER -> ConsentTexts(
        R.string.filter_title,
        R.string.filter_body,
        R.string.filter_submit
    )

    SetupStep.LOCATION -> ConsentTexts(
        R.string.location_title,
        R.string.location_body,
        R.string.location_allow
    )

    SetupStep.ACCESSIBILITY ->
        ConsentTexts(
            R.string.accessibility_title,
            R.string.accessibility_body,
            R.string.consent_agree
        )

    SetupStep.BACKGROUND_WORK ->
        ConsentTexts(R.string.background_title, R.string.background_body, R.string.open_settings)

    SetupStep.DEVICE_ADMIN, SetupStep.NOTIFICATIONS, SetupStep.USAGE_ACCESS ->
        error("No consent window for $step")
}
