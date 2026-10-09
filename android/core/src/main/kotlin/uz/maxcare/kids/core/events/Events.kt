package uz.maxcare.kids.core.events

/**
 * События защиты. Только факт и время — никакого содержимого (CLAUDE.md).
 * [wireName] совпадает с сервером: `backend/.../events/EventService.kt`.
 */
enum class EventType(val wireName: String) {
    /** Администратор устройства отключён не через приложение — возможная попытка обхода. */
    ADMIN_DISABLED("admin_disabled"),

    /** Родитель отключил защиту паролем. */
    PROTECTION_DISABLED("protection_disabled"),

    /** Пять неверных паролей подряд — ввод заблокирован. */
    PASSWORD_LOCKOUT("password_lockout"),

    /** Пароль родителя сброшен кодом восстановления. */
    PASSWORD_RECOVERED("password_recovered"),

    /** Уведомления MaxCare Kids выключены. */
    NOTIFICATIONS_OFF("notifications_off"),

    DEVICE_BOOTED("device_booted"),
    DEVICE_SHUTDOWN("device_shutdown")
}

/** Запись события в локальную очередь; отправка на сервер — отдельно и при первой возможности. */
interface EventRecorder {
    suspend fun record(type: EventType)
}

/**
 * Отправка накопленных событий; без событий — просто отметка «телефон на связи».
 * Вызывается периодически из постоянной службы и после загрузки телефона.
 */
interface EventSync {
    /** @return true, если сервер принял всё, что было в очереди. */
    suspend fun sync(): Boolean
}
