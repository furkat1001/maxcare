package uz.maxcare.kids.protection

import javax.inject.Inject
import uz.maxcare.kids.protection.service.ProtectionServiceStarter

/**
 * Снимает защиту полностью. Сам ничего не проверяет: вызывается только после пароля родителя
 * ([PasswordProtectedShutdown]) или из debug-команды для тестовых телефонов.
 *
 * Делает binder-вызовы — вызывать не на главном потоке (кроме debug-команды).
 */
internal class ProtectionRemover @Inject constructor(
    private val gateway: DevicePolicyGateway,
    private val serviceStarter: ProtectionServiceStarter
) {
    /** @return false, если защита не была включена и снимать нечего. */
    fun removeAll(): Boolean {
        when {
            gateway.isDeviceOwner() -> {
                // Пока права владельца есть — снимаем политики, затем сам режим.
                DEVICE_OWNER_RESTRICTIONS.forEach(gateway::clearUserRestriction)
                gateway.setOwnUninstallBlocked(false)
                gateway.clearDeviceOwner()
            }

            gateway.isAdminActive() -> gateway.removeActiveAdmin()

            else -> return false
        }
        serviceStarter.stop()
        return true
    }
}
