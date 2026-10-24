package uz.maxcare.backend.reports

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.db.instantOrNull
import uz.maxcare.backend.db.queryList
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.usage.AppUsageEntry

/**
 * Собирает итоги дня из того, что уже хранится. День — по местному времени родителя
 * ([offset]): события и точки хранятся в UTC, а время в приложениях — по дням телефона.
 */
class DailyReportService(private val db: Db) {

    /** @return null, если устройства нет или оно принадлежит другой семье. */
    suspend fun report(
        familyId: UUID,
        deviceId: UUID,
        day: LocalDate,
        offset: ZoneOffset
    ): DailyReport? = db.tx { c ->
        val lastSeen = c.queryOne(
            "select last_seen_at from devices where id = ? and family_id = ?",
            deviceId,
            familyId
        ) { Holder(it.instantOrNull("last_seen_at")) } ?: return@tx null

        val from = day.atStartOfDay().toInstant(offset)
        val to = day.plusDays(1).atStartOfDay().toInstant(offset)
        buildDailyReport(
            DayData(
                day = day,
                events = c.events(deviceId, from, to),
                usage = c.usage(deviceId, day),
                earlierPackages = c.earlierPackages(deviceId, day),
                points = c.points(deviceId, from, to),
                lastSeenAt = lastSeen.value
            )
        )
    }

    private class Holder(val value: Instant?)

    private fun Connection.events(deviceId: UUID, from: Instant, to: Instant) = queryList(
        """
            select type, detail, occurred_at from device_events
            where device_id = ? and occurred_at >= ? and occurred_at < ?
        """.trimIndent(),
        deviceId,
        from,
        to
    ) { ReportEvent(it.getString("type"), it.getString("detail"), it.instant("occurred_at")) }

    private fun Connection.usage(deviceId: UUID, day: LocalDate) = queryList(
        "select package_name, label, foreground_ms from app_usage where device_id = ? and day = ?",
        deviceId,
        day
    ) {
        AppUsageEntry(
            packageName = it.getString("package_name"),
            label = it.getString("label"),
            foregroundMs = it.getLong("foreground_ms")
        )
    }

    /** Пакеты за прошлые [HISTORY_DAYS] дней; null — истории нет совсем. */
    private fun Connection.earlierPackages(deviceId: UUID, day: LocalDate): Set<String>? {
        val packages = queryList(
            """
            select distinct package_name from app_usage
            where device_id = ? and day < ? and day >= ? and foreground_ms > 0
            """.trimIndent(),
            deviceId,
            day,
            day.minusDays(HISTORY_DAYS)
        ) { it.getString("package_name") }.toSet()
        return packages.ifEmpty { null }
    }

    private fun Connection.points(deviceId: UUID, from: Instant, to: Instant) = queryList(
        """
            select latitude, longitude, accuracy_m, recorded_at from device_locations
            where device_id = ? and recorded_at >= ? and recorded_at < ?
        """.trimIndent(),
        deviceId,
        from,
        to
    ) {
        ReportPoint(
            latitude = it.getDouble("latitude"),
            longitude = it.getDouble("longitude"),
            accuracyMeters = it.getObject("accuracy_m") as Double?,
            recordedAt = it.instant("recorded_at")
        )
    }

    // Столбцы not null: отсутствие значения — ошибка схемы, а не данных.
    private fun ResultSet.instant(column: String): Instant =
        requireNotNull(instantOrNull(column)) { "$column is null" }

    private companion object {
        const val HISTORY_DAYS = 7L
    }
}
