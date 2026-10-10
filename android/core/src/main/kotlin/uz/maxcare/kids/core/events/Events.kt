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
    DEVICE_SHUTDOWN("device_shutdown"),

    /** Фильтр сайтов выключен не через приложение: другой VPN или отключение в настройках. */
    VPN_OFF("vpn_off"),

    /** Включён «Частный DNS» с адресом сервера — DNS-запросы идут мимо фильтра. */
    PRIVATE_DNS_ON("private_dns_on"),

    /** Сайт заблокирован семейным DNS-провайдером: взрослый или вредоносный. Адрес — в detail. */
    SITE_BLOCKED_FILTERED("site_blocked_filtered"),

    /** Сайт азартных игр или ставок заблокирован. Адрес — в detail. */
    SITE_BLOCKED_GAMBLING("site_blocked_gambling")
}

/** Запись события в локальную очередь; отправка на сервер — отдельно и при первой возможности. */
interface EventRecorder {
    /**
     * @param detail уточнение для родителя — только для того, что защита пометила как опасное
     *   (сейчас — адрес заблокированного сайта). Адреса прочих сайтов не записываются (CLAUDE.md).
     */
    suspend fun record(type: EventType, detail: String? = null)
}

/**
 * Отправка накопленных событий; без событий — просто отметка «телефон на связи».
 * Вызывается периодически из постоянной службы и после загрузки телефона.
 */
interface EventSync {
    suspend fun sync(): SyncResult
}

enum class SyncResult {
    /** Сервер принял всё, что было в очереди. */
    DELIVERED,

    /** Нет сети или ошибка сервера — события остались в очереди, стоит повторить позже. */
    RETRY_LATER,

    /** Телефон не привязан или токен отозван (401) — повторять бессмысленно. */
    NOT_PAIRED
}
