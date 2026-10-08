package uz.maxcare.kids.protection

import javax.inject.Inject

/**
 * Только для debug-сборок: снимает политики и Device Owner, чтобы тестовый телефон
 * можно было вернуть в обычный режим без перепрошивки.
 */
internal class DeviceOwnerRelease @Inject constructor(private val gateway: DevicePolicyGateway) {
    /** @return false, если приложение не Device Owner и снимать нечего. */
    fun release(): Boolean {
        if (!gateway.isDeviceOwner()) return false
        // Пока права владельца есть — снимаем политики, затем сам режим.
        DEVICE_OWNER_RESTRICTIONS.forEach(gateway::clearUserRestriction)
        gateway.setOwnUninstallBlocked(false)
        gateway.clearDeviceOwner()
        return true
    }
}
