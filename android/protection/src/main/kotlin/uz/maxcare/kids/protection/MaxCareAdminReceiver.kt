package uz.maxcare.kids.protection

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.maxcare.kids.core.di.ApplicationScope
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType
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

    @Inject
    internal lateinit var remover: ProtectionRemover

    @Inject
    lateinit var events: EventRecorder

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
    // Запретить отключение в обычном режиме нельзя — только предупредить, а Samsung One UI 8
    // не показывает и это (docs/device-tests.md). Поэтому факт отключения фиксируется в onDisabled.
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.device_admin_disable_warning)

    // Вызывается до фактического отключения, поэтому режим здесь не пересчитываем, а сразу
    // убираем уведомление. Если защиту снял родитель паролем, это не попытка обхода — не пишем.
    override fun onDisabled(context: Context, intent: Intent) {
        serviceStarter.stop()
        if (remover.removedRecently()) return
        val pendingResult = goAsync()
        appScope.launch {
            try {
                events.record(EventType.ADMIN_DISABLED)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
