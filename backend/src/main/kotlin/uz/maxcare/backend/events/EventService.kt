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
    DEVICE_SHUTDOWN("device_shutdown");

    companion object {
        fun parse(value: String) = entries.firstOrNull { it.wireName == value }
    }
}

data class IncomingEvent(val id: UUID, val type: EventType, val occurredAt: Instant)

data class StoredEvent(val type: EventType, val occurredAt: Instant, val receivedAt: Instant)

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
                insert into device_events (device_id, id, type, occurred_at, received_at)
                values (?, ?, ?, ?, ?) on conflict do nothing
                """.trimIndent(),
                deviceId,
                event.id,
                event.type.wireName,
                event.occurredAt,
                now
            )
        }
    }

    /** @return null, если устройства нет или оно принадлежит другой семье. */
    suspend fun activity(familyId: UUID, deviceId: UUID): DeviceActivity? = db.tx { c ->
        val lastSeen = c.queryOne(
            "select last_seen_at from devices where id = ? and family_id = ?",
            deviceId,
            familyId
        ) { LastSeen(it.instantOrNull("last_seen_at")) } ?: return@tx null

        val events = c.queryList(
            """
            select type, occurred_at, received_at from device_events
            where device_id = ? order by received_at desc, occurred_at desc limit ?
            """.trimIndent(),
            deviceId,
            MAX_EVENTS
        ) { row ->
            EventType.parse(row.getString("type"))?.let { type ->
                StoredEvent(
                    type = type,
                    occurredAt = requireNotNull(row.instantOrNull("occurred_at")),
                    receivedAt = requireNotNull(row.instantOrNull("received_at"))
                )
            }
        }.filterNotNull()
        DeviceActivity(lastSeen.at, events)
    }

    // Обёртка: устройство без отметок (null) отличается от отсутствующего устройства.
    private class LastSeen(val at: Instant?)

    private companion object {
        const val MAX_EVENTS = 100
    }
}
