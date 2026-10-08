package uz.maxcare.kids.protection

import android.os.UserManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uz.maxcare.kids.core.di.IoDispatcher
import uz.maxcare.kids.core.protection.DeviceProtection
import uz.maxcare.kids.core.protection.ProtectionMode
import uz.maxcare.kids.protection.service.ProtectionServiceStarter

/** Ограничения пользователя, которые ставятся в режиме Device Owner. */
internal val DEVICE_OWNER_RESTRICTIONS = listOf(
    // Безопасный режим отключает сторонние приложения — главный способ обхода.
    UserManager.DISALLOW_SAFE_BOOT,
    UserManager.DISALLOW_FACTORY_RESET
)

@Singleton
internal class DevicePolicyProtection @Inject constructor(
    private val gateway: DevicePolicyGateway,
    private val serviceStarter: ProtectionServiceStarter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : DeviceProtection {
    private val _mode = MutableStateFlow(ProtectionMode.NONE)
    override val mode: StateFlow<ProtectionMode> = _mode.asStateFlow()

    // Вызовы DevicePolicyManager — binder-вызовы, поэтому не на главном потоке.
    override suspend fun applyPolicies(): ProtectionMode = withContext(ioDispatcher) {
        val current = when {
            gateway.isDeviceOwner() -> ProtectionMode.DEVICE_OWNER
            gateway.isAdminActive() -> ProtectionMode.DEVICE_ADMIN
            else -> ProtectionMode.NONE
        }
        if (current == ProtectionMode.DEVICE_OWNER) {
            gateway.setOwnUninstallBlocked(true)
            DEVICE_OWNER_RESTRICTIONS.forEach(gateway::addUserRestriction)
            // До запуска службы: иначе на Android 13+ уведомление может не показаться.
            gateway.lockNotificationPermissionGranted()
        }
        // Пока защита работает, постоянное уведомление обязательно (политика Google Play о мониторинге).
        if (current == ProtectionMode.NONE) serviceStarter.stop() else serviceStarter.start()
        _mode.value = current
        current
    }
}
