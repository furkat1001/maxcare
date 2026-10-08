package uz.maxcare.kids.core.protection

import kotlinx.coroutines.flow.StateFlow

/** Режим защиты устройства: от него зависит, какие политики доступны. */
enum class ProtectionMode {
    /** MaxCare Kids не является администратором устройства. */
    NONE,

    /** Активен Device Admin (обычная установка из Google Play). */
    DEVICE_ADMIN,

    /** Device Owner: политики нельзя снять из настроек телефона. */
    DEVICE_OWNER
}

interface DeviceProtection {
    val mode: StateFlow<ProtectionMode>

    /**
     * Определяет текущий режим и применяет все политики, доступные в нём.
     * Безопасно вызывать повторно: при запуске приложения и при включении администратора.
     */
    suspend fun applyPolicies(): ProtectionMode
}
