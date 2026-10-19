package uz.maxcare.backend.limits

import java.sql.Connection
import java.util.UUID
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.db.queryList
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.db.update

/** На что действует правило: одно приложение или группа приложений по категории Android. */
enum class LimitKind(val wire: String) {
    APP("app"),
    GROUP("group");

    companion object {
        fun parse(value: String) = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Правило ограничения времени.
 * @param dailyLimitMin минут в день, после — закрыто до завтра; null — без дневного лимита.
 * @param sessionMin сколько минут подряд можно, затем перерыв на [breakMin]; оба null — без цикла.
 */
data class LimitRule(
    val kind: LimitKind,
    val target: String,
    val dailyLimitMin: Int?,
    val sessionMin: Int?,
    val breakMin: Int?
)

/** Набор ограничений одного телефона: правила и приложения, которые не блокируются. */
data class AppLimits(val rules: List<LimitRule>, val alwaysAllowed: List<String>)

/** Ограничения времени в приложениях: родитель задаёт набор целиком, телефон забирает его. */
class AppLimitsService(private val db: Db) {

    /** @return ограничения или null, если устройства нет или оно из другой семьи. */
    suspend fun forParent(familyId: UUID, deviceId: UUID): AppLimits? = db.tx { c ->
        c.queryOne(
            "select 1 from devices where id = ? and family_id = ?",
            deviceId,
            familyId
        ) { true } ?: return@tx null
        load(c, deviceId)
    }

    /** Ограничения для самого телефона. */
    suspend fun forDevice(deviceId: UUID): AppLimits = db.tx { c -> load(c, deviceId) }

    /**
     * Заменяет набор целиком: так родителю не нужно согласовывать частичные правки.
     * @return false, если устройства нет или оно из другой семьи.
     */
    suspend fun replace(familyId: UUID, deviceId: UUID, limits: AppLimits): Boolean = db.tx { c ->
        c.queryOne(
            "select 1 from devices where id = ? and family_id = ?",
            deviceId,
            familyId
        ) { true } ?: return@tx false
        c.update("delete from app_limit_rules where device_id = ?", deviceId)
        c.update("delete from app_limit_allowed where device_id = ?", deviceId)
        limits.rules.forEach { rule ->
            c.update(
                """
                insert into app_limit_rules
                    (device_id, kind, target, daily_limit_min, session_min, break_min)
                values (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                deviceId,
                rule.kind.wire,
                rule.target,
                rule.dailyLimitMin,
                rule.sessionMin,
                rule.breakMin
            )
        }
        limits.alwaysAllowed.forEach { pkg ->
            c.update(
                "insert into app_limit_allowed (device_id, package_name) values (?, ?)",
                deviceId,
                pkg
            )
        }
        true
    }

    private fun load(c: Connection, deviceId: UUID): AppLimits {
        val rules = c.queryList(
            """
            select kind, target, daily_limit_min, session_min, break_min from app_limit_rules
            where device_id = ? order by kind, target
            """.trimIndent(),
            deviceId
        ) { row ->
            LimitRule(
                kind = requireNotNull(LimitKind.parse(row.getString("kind"))),
                target = row.getString("target"),
                dailyLimitMin = row.getObject("daily_limit_min") as Int?,
                sessionMin = row.getObject("session_min") as Int?,
                breakMin = row.getObject("break_min") as Int?
            )
        }
        val allowed = c.queryList(
            "select package_name from app_limit_allowed where device_id = ? order by package_name",
            deviceId
        ) { row -> row.getString("package_name") }
        return AppLimits(rules, allowed)
    }
}
