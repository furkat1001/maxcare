package uz.maxcare.backend.reports

import java.time.Instant
import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import uz.maxcare.backend.usage.AppUsageEntry

/**
 * Итоги дня по одному телефону для родителя. Только то, что сервер и так хранит: события,
 * время в приложениях, точки маршрута. Ни текста поиска, ни содержимого экрана (CLAUDE.md).
 */
@Serializable
data class DailyReport(
    val date: String,
    val screenTime: ScreenTime,
    val blocked: BlockedSummary,
    val limitsReached: List<LimitHit>,
    val bypassAttempts: List<EventCount>,
    /** Сколько раз родитель отключил защиту паролем — не обход, но родителю важно знать. */
    val protectionDisabled: Int,
    val location: LocationSummary?,
    val phone: PhoneSummary
)

/**
 * @param newApps приложения, которых не было в истории за прошлые дни; пусто, если истории
 *   ещё нет (иначе в первый день «новыми» оказались бы все приложения).
 */
@Serializable
data class ScreenTime(
    val totalMs: Long,
    val topApps: List<AppUsageEntry>,
    val newApps: List<AppUsageEntry>
)

/** Заблокированное за день по категориям; [topSites] — самые частые заблокированные адреса. */
@Serializable
data class BlockedSummary(
    val sites: Int,
    val gambling: Int,
    val search: Int,
    val images: Int,
    val messenger: Int,
    val topSites: List<String>
)

/** @param label название приложения из времени в приложениях, если оно известно. */
@Serializable
data class LimitHit(val packageName: String, val label: String?, val count: Int)

@Serializable
data class EventCount(val type: String, val count: Int, val lastAt: String)

/** @param distanceMeters примерный путь: точки с плохой точностью не учитываются. */
@Serializable
data class LocationSummary(
    val points: Int,
    val distanceMeters: Long,
    val firstAt: String,
    val lastAt: String
)

@Serializable
data class PhoneSummary(val boots: Int, val shutdowns: Int, val lastSeenAt: String?)

/** Событие из базы — то, что нужно отчёту. */
data class ReportEvent(val type: String, val detail: String?, val occurredAt: Instant)

data class ReportPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val recordedAt: Instant
)

/** Данные за день, уже выбранные из базы. */
data class DayData(
    val day: LocalDate,
    val events: List<ReportEvent>,
    val usage: List<AppUsageEntry>,
    /** Пакеты из прошлых дней; null — истории нет. */
    val earlierPackages: Set<String>?,
    val points: List<ReportPoint>,
    val lastSeenAt: Instant?
)

private val BLOCK_TYPES = mapOf(
    "site_blocked_filtered" to "sites",
    "site_blocked_gambling" to "gambling",
    "search_blocked" to "search",
    "image_blocked_nudity" to "images",
    "image_blocked_violence" to "images",
    "text_flagged_messenger" to "messenger"
)

/** Попытки обойти защиту или признаки, что она перестала работать. */
private val BYPASS_TYPES = setOf(
    "admin_disabled",
    "uninstall_attempt_blocked",
    "notifications_off",
    "accessibility_off",
    "vpn_off",
    "private_dns_on",
    "location_off",
    "location_denied",
    "password_lockout"
)

private const val TOP_APPS = 5
private const val TOP_SITES = 5
private const val MAX_ACCURACY_METERS = 200.0

fun buildDailyReport(data: DayData): DailyReport {
    val byType = data.events.groupBy { it.type }
    fun count(type: String) = byType[type]?.size ?: 0
    val labels = data.usage.associate { it.packageName to it.label }
    return DailyReport(
        date = data.day.toString(),
        screenTime = screenTime(data),
        blocked = blockedSummary(data.events),
        limitsReached = (byType["app_limit_reached"].orEmpty())
            .mapNotNull { it.detail }
            .groupingBy { it }
            .eachCount()
            .map { (pkg, n) -> LimitHit(pkg, labels[pkg], n) }
            .sortedByDescending { it.count },
        bypassAttempts = BYPASS_TYPES.mapNotNull { type ->
            byType[type]?.let { list ->
                EventCount(type, list.size, list.maxOf { it.occurredAt }.toString())
            }
        }.sortedByDescending { it.count },
        protectionDisabled = count("protection_disabled"),
        location = locationSummary(data.points),
        phone = PhoneSummary(
            boots = count("device_booted"),
            shutdowns = count("device_shutdown"),
            lastSeenAt = data.lastSeenAt?.toString()
        )
    )
}

private fun screenTime(data: DayData): ScreenTime {
    val used = data.usage.filter { it.foregroundMs > 0 }.sortedByDescending { it.foregroundMs }
    val earlier = data.earlierPackages
    return ScreenTime(
        totalMs = used.sumOf { it.foregroundMs },
        topApps = used.take(TOP_APPS),
        newApps = if (earlier == null) emptyList() else used.filter { it.packageName !in earlier }
    )
}

private fun blockedSummary(events: List<ReportEvent>): BlockedSummary {
    val counts = events.mapNotNull { BLOCK_TYPES[it.type] }.groupingBy { it }.eachCount()
    val topSites = events
        .filter { it.type == "site_blocked_filtered" || it.type == "site_blocked_gambling" }
        .mapNotNull { it.detail }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(TOP_SITES)
        .map { it.key }
    return BlockedSummary(
        sites = counts["sites"] ?: 0,
        gambling = counts["gambling"] ?: 0,
        search = counts["search"] ?: 0,
        images = counts["images"] ?: 0,
        messenger = counts["messenger"] ?: 0,
        topSites = topSites
    )
}

private fun locationSummary(points: List<ReportPoint>): LocationSummary? {
    if (points.isEmpty()) return null
    val sorted = points.sortedBy { it.recordedAt }
    val precise = sorted.filter { (it.accuracyMeters ?: 0.0) <= MAX_ACCURACY_METERS }
    val meters = precise.zipWithNext { a, b -> distanceMeters(a, b) }.sum()
    return LocationSummary(
        points = sorted.size,
        distanceMeters = meters.roundToLong(),
        firstAt = sorted.first().recordedAt.toString(),
        lastAt = sorted.last().recordedAt.toString()
    )
}

private const val EARTH_RADIUS_M = 6_371_000.0

/** Расстояние по поверхности Земли (формула гаверсинусов). */
internal fun distanceMeters(a: ReportPoint, b: ReportPoint): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}
