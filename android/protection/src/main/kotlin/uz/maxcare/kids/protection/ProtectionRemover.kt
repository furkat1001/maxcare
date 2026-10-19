package uz.maxcare.kids.protection

import javax.inject.Inject
import javax.inject.Singleton
import uz.maxcare.kids.core.filter.NetworkFilter
import uz.maxcare.kids.protection.password.DeviceClock
import uz.maxcare.kids.protection.service.ProtectionServiceStarter

/**
 * Снимает защиту полностью. Сам ничего не проверяет: вызывается только после пароля родителя
 * ([PasswordProtectedShutdown]) или из debug-команды для тестовых телефонов.
 *
 * Делает binder-вызовы — вызывать не на главном потоке (кроме debug-команды).
 */
@Singleton
internal class ProtectionRemover @Inject constructor(
    private val gateway: DevicePolicyGateway,
    private val serviceStarter: ProtectionServiceStarter,
    private val networkFilter: NetworkFilter,
    private val clock: DeviceClock
) {
    @Volatile
    private var lastRemovalAt: Long? = null

    /** @return false, если защита не была включена и снимать нечего. */
    fun removeAll(): Boolean {
        // До снятия: система пришлёт onDisabled позже, асинхронно.
        lastRemovalAt = clock.elapsedRealtime()
        when {
            gateway.isDeviceOwner() -> {
                // Пока права владельца есть — снимаем политики, затем сам режим.
                DEVICE_OWNER_RESTRICTIONS.forEach(gateway::clearUserRestriction)
                gateway.setLocationLocked(false)
                gateway.setAlwaysOnVpn(false)
                gateway.setOwnUninstallBlocked(false)
                gateway.clearDeviceOwner()
            }

            gateway.isAdminActive() -> gateway.removeActiveAdmin()

            else -> return false
        }
        serviceStarter.stop()
        networkFilter.stop()
        return true
    }

    /**
     * Защиту только что снимало само приложение. Отличает отключение администратора паролем
     * родителя от отключения в Настройках: система сообщает об обоих одинаково (onDisabled).
     */
    fun removedRecently(): Boolean {
        val last = lastRemovalAt ?: return false
        return clock.elapsedRealtime() - last < RECENT_REMOVAL_MILLIS
    }

    private companion object {
        const val RECENT_REMOVAL_MILLIS = 60_000L
    }
}
