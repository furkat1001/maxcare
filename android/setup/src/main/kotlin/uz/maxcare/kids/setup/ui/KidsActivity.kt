package uz.maxcare.kids.setup.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    val system = rememberSystemActions(viewModel)

    when (val screen = state.screen) {
        KidsScreen.Loading -> ScreenLayout(title = "", body = "", state = state.copy(busy = true)) {
        }

        is KidsScreen.Setup -> SetupStepScreen(screen, state, viewModel, system)

        is KidsScreen.Home -> HomeScreen(screen, state, viewModel)

        KidsScreen.DisableProtection -> DisableProtectionScreen(state, viewModel)

        KidsScreen.RecoverPassword -> RecoverPasswordScreen(state, viewModel)

        KidsScreen.Disabled -> DisabledScreen(state, viewModel)
    }
}

/** Системные диалоги и экраны настроек для шагов мастера; результат возвращается во ViewModel. */
@Composable
private fun rememberSystemActions(viewModel: KidsViewModel): SystemActions {
    val packageName = LocalContext.current.packageName
    val adminLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onDeviceAdminResult()
        }
    val filterLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onWebFilterResult()
    }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onNotificationPermissionResult()
        }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onLocationPermissionResult()
    }
    val usageAccessLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onUsageAccessResult()
        }
    val context = LocalContext.current
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refresh()
        }
    return SystemActions(
        activateDeviceAdmin = { adminLauncher.launch(viewModel.deviceAdminIntent()) },
        enableWebFilter = {
            val intent = viewModel.webFilterIntent()
            if (intent == null) viewModel.onWebFilterResult() else filterLauncher.launch(intent)
        },
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
        requestLocation = { requestLocation(context, locationLauncher::launch) },
        openUsageAccessSettings = {
            usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        },
        openAppSettings = { settingsLauncher.launch(appSettingsIntent(packageName)) }
    )
}

/**
 * Геолокация выдаётся в два шага: сначала доступ к местоположению обычным диалогом, затем, с
 * Android 10, фоновое «Разрешить всегда» — его система в диалоге не показывает, поэтому для него
 * шаг предлагает «Открыть настройки». Здесь запрашиваем диалогом то, что можно.
 */
private fun requestLocation(context: Context, launch: (Array<String>) -> Unit) {
    val foreground = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    val hasForeground = foreground.any {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
    val needsBackground = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    when {
        !hasForeground -> launch(foreground)
        needsBackground -> launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        else -> launch(foreground)
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
