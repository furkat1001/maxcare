package uz.maxcare.kids.setup.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * Единственный экран приложения ребёнка: настройка, статус, отключение защиты.
 * AppCompatActivity — ради выбора языка на Android 8–12: там его применяет только AppCompat.
 */
@AndroidEntryPoint
class KidsActivity : AppCompatActivity() {
    private val viewModel: KidsViewModel by viewModels()
    private val settingsReturn = SettingsReturn(this)

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
                Surface { KidsApp(viewModel, settingsReturn) }
            }
        }
    }

    // В том числе после возврата из системных настроек: пункт мог уже включиться.
    override fun onResume() {
        super.onResume()
        settingsReturn.cancel()
        viewModel.refresh()
    }
}

@Composable
private fun KidsApp(viewModel: KidsViewModel, settingsReturn: SettingsReturn) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val system = rememberSystemActions(viewModel, settingsReturn)
    LaunchedEffect(viewModel) {
        viewModel.launches.collect(system::launch)
    }

    when (val screen = state.screen) {
        KidsScreen.Loading -> ScreenLayout(title = "", body = "", state = state.copy(busy = true)) {
        }

        is KidsScreen.Connect -> ConnectScreen(screen, state, viewModel)

        is KidsScreen.Permissions -> PermissionsScreen(screen, state, viewModel)

        is KidsScreen.Home -> HomeScreen(screen, state, viewModel)

        KidsScreen.DisableProtection -> DisableProtectionScreen(state, viewModel)

        KidsScreen.RecoverPassword -> RecoverPasswordScreen(state, viewModel)

        KidsScreen.Disabled -> DisabledScreen(state, viewModel)
    }
}

/** Системные диалоги и экраны настроек; результат возвращается во ViewModel. */
// Лаунчеры результатов Compose создаются только внутри composable — дробить некуда.
@Suppress("LongMethod")
@Composable
private fun rememberSystemActions(
    viewModel: KidsViewModel,
    settingsReturn: SettingsReturn
): SystemActions {
    val context = LocalContext.current
    val packageName = context.packageName
    fun result(step: SetupStep) = { _: Any? -> viewModel.onSystemResult(step) }
    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        result(SetupStep.DEVICE_ADMIN)
    )
    val filterLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        result(SetupStep.WEB_FILTER)
    )
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        result(SetupStep.NOTIFICATIONS)
    )
    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        result(SetupStep.NOTIFICATIONS)
    )
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        result(SetupStep.LOCATION)
    )
    val usageAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        result(SetupStep.USAGE_ACCESS)
    )
    val accessibilityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        result(SetupStep.ACCESSIBILITY)
    )
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        result(SetupStep.BACKGROUND_WORK)
    )

    // Экран Настроек для пункта: окно доверия для защиты от удаления и автовозврат.
    fun ActivityResultLauncher<Intent>.openSettings(step: SetupStep, vararg intents: Intent) {
        viewModel.beforeSettingsScreen()
        // Автозапуск прошивки проверить нельзя: с этой страницы родитель возвращается сам.
        if (step != SetupStep.BACKGROUND_WORK) settingsReturn.watch { viewModel.isGranted(step) }
        launchFirstAvailable(intents.toList())
    }

    return SystemActions { request ->
        when (request.step) {
            SetupStep.DEVICE_ADMIN -> adminLauncher.launch(viewModel.deviceAdminIntent())

            SetupStep.WEB_FILTER -> {
                val intent = viewModel.webFilterIntent()
                if (intent == null) {
                    viewModel.onSystemResult(SetupStep.WEB_FILTER)
                } else {
                    filterLauncher.launch(intent)
                }
            }

            SetupStep.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !request.viaSettings) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // До Android 13 разрешения нет, а после двух отказов диалог не появляется:
                    // уведомления включаются только в настройках.
                    notificationSettingsLauncher.openSettings(
                        SetupStep.NOTIFICATIONS,
                        notificationSettingsIntent(packageName)
                    )
                }

            SetupStep.LOCATION -> {
                // Разрешение «всегда» система показывает экраном Настроек.
                viewModel.beforeSettingsScreen()
                settingsReturn.watch { viewModel.isGranted(SetupStep.LOCATION) }
                requestLocation(context, locationLauncher::launch)
            }

            // Сразу страница MaxCare Kids, где Android это умеет; иначе — общий список.
            SetupStep.USAGE_ACCESS -> usageAccessLauncher.openSettings(
                SetupStep.USAGE_ACCESS,
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:$packageName".toUri()),
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )

            // Страницы самой службы в публичном API нет: открывается общий список.
            SetupStep.ACCESSIBILITY -> accessibilityLauncher.openSettings(
                SetupStep.ACCESSIBILITY,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )

            // Здесь же на Xiaomi и других — «Автозапуск» и ограничения батареи.
            SetupStep.BACKGROUND_WORK -> backgroundLauncher.openSettings(
                SetupStep.BACKGROUND_WORK,
                appSettingsIntent(packageName)
            )
        }
    }
}

/** Первый экран, который есть на этом телефоне: у прошивок разный набор экранов Настроек. */
private fun ActivityResultLauncher<Intent>.launchFirstAvailable(intents: List<Intent>) {
    for ((index, intent) in intents.withIndex()) {
        try {
            launch(intent)
            return
        } catch (notFound: ActivityNotFoundException) {
            if (index == intents.lastIndex) throw notFound
        }
    }
}

/**
 * Геолокация выдаётся в два шага: сначала доступ к местоположению обычным диалогом, затем, с
 * Android 10, фоновое «Разрешить всегда» — его система показывает экраном Настроек. ViewModel
 * просит второй шаг сразу после первого.
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

private fun appSettingsIntent(packageName: String): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", packageName, null)
)
