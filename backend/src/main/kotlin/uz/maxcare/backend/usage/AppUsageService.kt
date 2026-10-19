package uz.maxcare.backend.usage

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.db.instantOrNull
import uz.maxcare.backend.db.queryList
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.db.update

/** Время одного приложения на экране за день. */
data class AppUsage(val packageName: String, val label: String, val foregroundMs: Long)

/** Снимок дня с телефона: все приложения, которые были на экране в этот день. */
data class DayUsage(val day: LocalDate, val apps: List<AppUsage>)

/** Время в приложениях за день для родителя; [updatedAt] — null, если за день данных нет. */
data class DayUsageReport(val day: LocalDate, val updatedAt: Instant?, val apps: List<AppUsage>)

/** Время в приложениях: приём снимков от телефона и отчёт за день для родителя. */
class AppUsageService(private val db: Db, private val clock: Clock) {

    /** Сегодняшняя дата по часам сервера (UTC) — для проверки дат в снимке. */
    fun today(): LocalDate = LocalDate.now(clock)

    /**
     * Заменяет присланные дни целиком (повтор того же снимка ничего не меняет) и отмечает,
     * что телефон на связи.
     */
    suspend fun record(deviceId: UUID, days: List<DayUsage>) = db.tx { c ->
        val now = clock.instant()
        c.update("update devices set last_seen_at = ? where id = ?", now, deviceId)
        days.forEach { day ->
            c.update("delete from app_usage where device_id = ? and day = ?", deviceId, day.day)
            day.apps.forEach { app ->
                c.update(
                    """
                    insert into app_usage
                        (device_id, day, package_name, label, foreground_ms, updated_at)
                    values (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    deviceId,
                    day.day,
                    app.packageName,
                    app.label,
                    app.foregroundMs,
                    now
                )
            }
        }
    }

    /**
     * Время в приложениях за [day], по убыванию времени.
     * @return отчёт (может быть пустым) или null, если устройства нет или оно из другой семьи.
     */
    suspend fun day(familyId: UUID, deviceId: UUID, day: LocalDate): DayUsageReport? = db.tx { c ->
        c.queryOne(
            "select 1 from devices where id = ? and family_id = ?",
            deviceId,
            familyId
        ) { true } ?: return@tx null

        val rows = c.queryList(
            """
            select package_name, label, foreground_ms, updated_at from app_usage
            where device_id = ? and day = ?
            order by foreground_ms desc, label
            """.trimIndent(),
            deviceId,
            day
        ) { row ->
            AppUsage(
                packageName = row.getString("package_name"),
                label = row.getString("label"),
                foregroundMs = row.getLong("foreground_ms")
            ) to row.instantOrNull("updated_at")
        }
        // Снимок дня пишется целиком, поэтому у всех строк одно время обновления.
        DayUsageReport(day, rows.firstOrNull()?.second, rows.map { it.first })
    }
}
