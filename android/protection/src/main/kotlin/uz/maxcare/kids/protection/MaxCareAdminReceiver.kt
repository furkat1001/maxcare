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
}
