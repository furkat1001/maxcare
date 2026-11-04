package uz.maxcare.backend.events

import java.time.Clock
import java.time.Instant
import java.util.UUID
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.db.instantOrNull
import uz.maxcare.backend.db.queryList
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.db.update

/** События защиты. Те же имена отправляет телефон: `android/core/.../EventType.kt`. */
enum class EventType(val wireName: String) {
    ADMIN_DISABLED("admin_disabled"),
    PROTECTION_DISABLED("protection_disabled"),
    PASSWORD_LOCKOUT("password_lockout"),
    PASSWORD_RECOVERED("password_recovered"),
    NOTIFICATIONS_OFF("notifications_off"),
    DEVICE_BOOTED("device_booted"),
    DEVICE_SHUTDOWN("device_shutdown"),
    VPN_OFF("vpn_off"),
    PRIVATE_DNS_ON("private_dns_on"),
    SITE_BLOCKED_FILTERED("site_blocked_filtered"),
    SITE_BLOCKED_GAMBLING("site_blocked_gambling"),
    SEARCH_BLOCKED("search_blocked"),
    TEXT_FLAGGED_MESSENGER("text_flagged_messenger"),
    IMAGE_BLOCKED_NUDITY("image_blocked_nudity"),
    IMAGE_BLOCKED_VIOLENCE("image_blocked_violence"),
    UNINSTALL_ATTEMPT_BLOCKED("uninstall_attempt_blocked"),
    ACCESSIBILITY_OFF("accessibility_off"),
    APP_LIMIT_REACHED("app_limit_reached"),
    LOCATION_OFF("location_off"),
    LOCATION_DENIED("location_denied"),
    LOCATION_ON("location_on");

    companion object {
        fun parse(value: String) = entries.firstOrNull { it.wireName == value }
    }
}

data class IncomingEvent(
    val id: UUID,
    val type: EventType,
    val occurredAt: Instant,
    val detail: String? = null
)

data class StoredEvent(
    val type: EventType,
    val occurredAt: Instant,
    val receivedAt: Instant,
    val detail: String? = null
)

data class DeviceActivity(val lastSeenAt: Instant?, val events: List<StoredEvent>)

class EventService(private val db: Db, private val clock: Clock) {

    /**
     * Сохраняет события и отмечает, что телефон на связи (в том числе при пустой пачке).
     * @return сколько событий новых; повторно присланные не считаются.
     */
    suspend fun record(deviceId: UUID, events: List<IncomingEvent>): Int = db.tx { c ->
        val now = clock.instant()
        c.update("update devices set last_seen_at = ? where id = ?", now, deviceId)
        events.sumOf { event ->
            c.update(
                """
                insert into device_events (device_id, id, type, occurred_at, received_at, detail)
                values (?, ?, ?, ?, ?, ?) on conflict do nothing
                """.trimIndent(),
                deviceId,
                event.id,
                event.type.wireName,
                event.occurredAt,
                now,
                event.detail
            )
        }
    }

    /**
     * @param window события, случившиеся в [EventWindow.from, EventWindow.to) — для ленты дня;
     *   без окна — последние полученные сервером.
     * @return null, если устройства нет или оно принадлежит другой семье.
     */
    suspend fun activity(
        familyId: UUID,
        deviceId: UUID,
        window: EventWindow? = null
    ): DeviceActivity? = db.tx { c ->
        val lastSeen = c.queryOne(
            "select last_seen_at from devices where id = ? and family_id = ?",
            deviceId,
            familyId
        ) { LastSeen(it.instantOrNull("last_seen_at")) } ?: return@tx null

        val (sql, params) = if (window == null) {
            """
            select type, occurred_at, received_at, detail from device_events
            where device_id = ? order by received_at desc, occurred_at desc limit ?
            """.trimIndent() to arrayOf<Any?>(deviceId, MAX_EVENTS)
        } else {
            """
            select type, occurred_at, received_at, detail from device_events
            where device_id = ? and occurred_at >= ? and occurred_at < ?
            order by occurred_at desc limit ?
            """.trimIndent() to arrayOf<Any?>(deviceId, window.from, window.to, MAX_WINDOW_EVENTS)
        }
        val events = c.queryList(sql, *params) { row ->
            EventType.parse(row.getString("type"))?.let { type ->
                StoredEvent(
                    type = type,
                    occurredAt = requireNotNull(row.instantOrNull("occurred_at")),
                    receivedAt = requireNotNull(row.instantOrNull("received_at")),
                    detail = row.getString("detail")
                )
            }
        }.filterNotNull()
        DeviceActivity(lastSeen.at, events)
    }

    // Обёртка: устройство без отметок (null) отличается от отсутствующего устройства.
    private class LastSeen(val at: Instant?)

    private companion object {
        const val MAX_EVENTS = 100

        // День с сотнями блокировок — редкость; лента всё равно склеивает повторы.
        const val MAX_WINDOW_EVENTS = 500
    }
}

/** Промежуток времени события на телефоне; [to] не включается. */
data class EventWindow(val from: Instant, val to: Instant)
