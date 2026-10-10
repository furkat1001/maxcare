package uz.maxcare.kids.protection

import android.os.Build
import android.os.UserManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uz.maxcare.kids.core.di.IoDispatcher
import uz.maxcare.kids.core.filter.NetworkFilter
import uz.maxcare.kids.core.protection.DeviceProtection
import uz.maxcare.kids.core.protection.ProtectionMode
import uz.maxcare.kids.protection.service.NotificationStateMonitor
import uz.maxcare.kids.protection.service.ProtectionServiceStarter

/** Ограничения пользователя, которые ставятся в режиме Device Owner. */
internal val DEVICE_OWNER_RESTRICTIONS = buildList {
    // Безопасный режим отключает сторонние приложения — главный способ обхода.
    add(UserManager.DISALLOW_SAFE_BOOT)
    add(UserManager.DISALLOW_FACTORY_RESET)
    // Фильтр сайтов: нельзя сменить VPN и включить «Частный DNS» мимо фильтра.
    add(UserManager.DISALLOW_CONFIG_VPN)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
}

@Singleton
internal class DevicePolicyProtection @Inject constructor(
    private val gateway: DevicePolicyGateway,
    private val serviceStarter: ProtectionServiceStarter,
    private val notificationMonitor: NotificationStateMonitor,
    private val networkFilter: NetworkFilter,
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
            // Система сама запустит VPN фильтра и не даст работать сети без него.
            gateway.setAlwaysOnVpn(true)
        }
        // Пока защита работает, постоянное уведомление обязательно (политика Google Play о мониторинге).
        if (current == ProtectionMode.NONE) serviceStarter.stop() else serviceStarter.start()
        // Не только раз в 15 минут из службы: applyPolicies вызывается на каждом шаге мастера и при
        // открытии приложения, так включение уведомлений замечается сразу. Иначе выключение вскоре
        // после настройки не отличить от «были выключены всегда» (найдено на Samsung).
        if (current == ProtectionMode.NONE) {
            networkFilter.stop()
        } else {
            notificationMonitor.check()
            networkFilter.start()
            networkFilter.checkBypasses()
        }
        _mode.value = current
        current
    }
}
