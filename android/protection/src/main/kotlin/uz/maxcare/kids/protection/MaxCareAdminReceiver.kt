package uz.maxcare.kids.protection

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.maxcare.kids.core.di.ApplicationScope
import uz.maxcare.kids.core.protection.DeviceProtection
import uz.maxcare.kids.protection.service.ProtectionServiceStarter

/**
 * Администратор устройства MaxCare Kids. Имя класса используется в
 * `adb shell dpm set-device-owner uz.maxcare.kids/.protection.MaxCareAdminReceiver` — не переименовывать.
 */
@AndroidEntryPoint
class MaxCareAdminReceiver : DeviceAdminReceiver() {
    @Inject
    lateinit var deviceProtection: DeviceProtection

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    @Inject
    internal lateinit var serviceStarter: ProtectionServiceStarter

    override fun onEnabled(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        appScope.launch {
            try {
                deviceProtection.applyPolicies()
            } finally {
                pendingResult.finish()
            }
        }
    }

    // Показывается в системном диалоге, когда администратора пытаются отключить из Настроек.
    // Запретить отключение в обычном режиме нельзя — только предупредить; перехват экрана
    // Настроек появится вместе со службой доступности (спринт 3).
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.device_admin_disable_warning)

    // Вызывается до фактического отключения, поэтому режим здесь не пересчитываем, а сразу
    // убираем уведомление. Событие «администратор отключён» для отчёта — вместе с очередью событий.
    override fun onDisabled(context: Context, intent: Intent) {
        serviceStarter.stop()
    }
}
