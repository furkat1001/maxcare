package uz.maxcare.backend.location

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.db.instantOrNull
import uz.maxcare.backend.db.queryList
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.db.update

/** Одна геопозиция телефона ребёнка. */
data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val recordedAt: Instant,
    val receivedAt: Instant
)

/** Отметка, присланная телефоном (id создан на телефоне для защиты от дублей). */
data class IncomingLocation(
    val id: UUID,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val recordedAt: Instant
)

/** Геопозиции телефонов: приём от ребёнка и последняя точка для родителя. */
class LocationService(private val db: Db, private val clock: Clock) {

    /** Сохраняет отметку и отмечает, что телефон на связи. @return true, если отметка новая. */
    suspend fun record(deviceId: UUID, location: IncomingLocation): Boolean =
        record(deviceId, listOf(location)) == 1

    /**
     * Сохраняет пачку отметок и отмечает, что телефон на связи (в том числе при пустой пачке).
     * @return сколько отметок новых; повторно присланные (по id) не считаются.
     */
    suspend fun record(deviceId: UUID, locations: List<IncomingLocation>): Int = db.tx { c ->
        val now = clock.instant()
        c.update("update devices set last_seen_at = ? where id = ?", now, deviceId)
        locations.sumOf { location ->
            c.update(
                """
                insert into device_locations
                    (device_id, id, latitude, longitude, accuracy_m, recorded_at, received_at)
                values (?, ?, ?, ?, ?, ?, ?) on conflict do nothing
                """.trimIndent(),
                deviceId,
                location.id,
                location.latitude,
                location.longitude,
                location.accuracyMeters,
                location.recordedAt,
                now
            )
        }
    }

    /**
     * Последняя позиция устройства семьи.
     * @return [Found] с позицией (или null, если телефон её ещё не слал),
     *   либо [NotFound], если устройства нет или оно из другой семьи.
     */
    suspend fun latest(familyId: UUID, deviceId: UUID): LocationResult = db.tx { c ->
        val owned = c.queryOne(
            "select 1 from devices where id = ? and family_id = ?",
            deviceId,
            familyId
        ) { true } ?: return@tx LocationResult.NotFound

        val location = c.queryOne(
            """
            $LOCATION_COLUMNS from device_locations where device_id = ?
            order by received_at desc, recorded_at desc limit 1
            """.trimIndent(),
            deviceId
        ) { row -> row.toLocation() }
        LocationResult.Found(location)
    }

    /**
     * Трек устройства за период (для вида «где был ребёнок за день»), по возрастанию времени.
     * @param since начало окна; по умолчанию последние сутки.
     * @return список точек (может быть пустым) или null, если устройства нет или оно из другой семьи.
     */
    suspend fun history(
        familyId: UUID,
        deviceId: UUID,
        since: Instant? = null,
        limit: Int = MAX_HISTORY
    ): List<DeviceLocation>? = db.tx { c ->
        c.queryOne(
            "select 1 from devices where id = ? and family_id = ?",
            deviceId,
            familyId
        ) { true } ?: return@tx null

        val from = since ?: clock.instant().minus(DEFAULT_WINDOW)
        // Берём самые свежие точки окна, затем разворачиваем в хронологический порядок для маршрута.
        c.queryList(
            """
            $LOCATION_COLUMNS from device_locations
            where device_id = ? and received_at >= ?
            order by received_at desc, recorded_at desc limit ?
            """.trimIndent(),
            deviceId,
            from,
            limit.coerceAtMost(MAX_HISTORY)
        ) { row -> row.toLocation() }.reversed()
    }

    private companion object {
        const val LOCATION_COLUMNS =
            "select latitude, longitude, accuracy_m, recorded_at, received_at"
        const val MAX_HISTORY = 500
        val DEFAULT_WINDOW: Duration = Duration.ofHours(24)
    }
}

private fun java.sql.ResultSet.toLocation() = DeviceLocation(
    latitude = getDouble("latitude"),
    longitude = getDouble("longitude"),
    accuracyMeters = (getObject("accuracy_m") as? Number)?.toDouble(),
    recordedAt = requireNotNull(instantOrNull("recorded_at")),
    receivedAt = requireNotNull(instantOrNull("received_at"))
)

sealed interface LocationResult {
    data class Found(val location: DeviceLocation?) : LocationResult
    data object NotFound : LocationResult
}
