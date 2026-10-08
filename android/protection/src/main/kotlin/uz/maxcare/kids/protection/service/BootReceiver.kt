package uz.maxcare.kids.protection.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.maxcare.kids.core.di.ApplicationScope
import uz.maxcare.kids.core.protection.DeviceProtection

/**
 * После перезагрузки телефона и после обновления приложения восстанавливает защиту:
 * повторно применяет политики и запускает постоянное уведомление.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var deviceProtection: DeviceProtection

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        // Ресивер открыт для системных событий; чужие интенты с другими действиями игнорируем.
        if (intent.action !in HANDLED_ACTIONS) return
        val pendingResult = goAsync()
        appScope.launch {
            try {
                deviceProtection.applyPolicies()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
