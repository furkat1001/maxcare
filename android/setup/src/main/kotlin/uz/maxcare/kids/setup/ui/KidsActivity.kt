package uz.maxcare.kids.setup.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint

/** Единственный экран приложения ребёнка: мастер настройки, статус, отключение защиты. */
@AndroidEntryPoint
class KidsActivity : ComponentActivity() {
    private val viewModel: KidsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Здесь вводится пароль родителя: запрещаем скриншоты и запись экрана.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContent {
            MaxCareTheme {
                Surface { KidsApp(viewModel) }
            }
        }
    }

    // В том числе после возврата из системных настроек: шаг мог уже выполниться.
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

@Composable
private fun KidsApp(viewModel: KidsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val packageName = LocalContext.current.packageName
    val adminLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onDeviceAdminResult()
        }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onNotificationPermissionResult()
        }
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refresh()
        }
    val system = SystemActions(
        activateDeviceAdmin = { adminLauncher.launch(viewModel.deviceAdminIntent()) },
        requestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // До Android 13 разрешения нет: уведомления включаются только в настройках.
                settingsLauncher.launch(notificationSettingsIntent(packageName))
            }
        },
        openNotificationSettings = {
            settingsLauncher.launch(notificationSettingsIntent(packageName))
        },
        openAppSettings = { settingsLauncher.launch(appSettingsIntent(packageName)) }
    )

    when (val screen = state.screen) {
        KidsScreen.Loading -> ScreenLayout(title = "", body = "", state = state.copy(busy = true)) {
        }

        is KidsScreen.Setup -> SetupStepScreen(screen, state, viewModel, system)

        is KidsScreen.Home -> HomeScreen(screen, state, viewModel)

        KidsScreen.DisableProtection -> DisableProtectionScreen(state, viewModel)

        KidsScreen.RecoverPassword -> RecoverPasswordScreen(state, viewModel)

        KidsScreen.Disabled -> DisabledScreen(state)
    }
}

private fun notificationSettingsIntent(packageName: String): Intent = Intent(
    Settings.ACTION_APP_NOTIFICATION_SETTINGS
).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

// Здесь же на Xiaomi и других — «Автозапуск» и ограничения батареи.
private fun appSettingsIntent(packageName: String): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", packageName, null)
)
