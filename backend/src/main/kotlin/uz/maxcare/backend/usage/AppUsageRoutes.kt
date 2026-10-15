package uz.maxcare.backend.usage

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.Serializable
import uz.maxcare.backend.devices.DevicePrincipal
import uz.maxcare.backend.devices.PARENT_AUTH
import uz.maxcare.backend.events.DEVICE_AUTH
import uz.maxcare.backend.http.ErrorCodes
import uz.maxcare.backend.http.respondError
import uz.maxcare.backend.parents.ParentPrincipal
import uz.maxcare.backend.ratelimit.RateLimiter
import uz.maxcare.backend.ratelimit.check

@Serializable
data class AppUsageEntry(val packageName: String, val label: String, val foregroundMs: Long)

@Serializable
data class DayUsageRequest(val date: String, val apps: List<AppUsageEntry> = emptyList())

@Serializable
data class AppUsageUploadRequest(val days: List<DayUsageRequest> = emptyList())

@Serializable
data class AppUsageUploadResponse(val accepted: Int)

@Serializable
data class DayUsageResponse(
    val date: String,
    val updatedAt: String? = null,
    val apps: List<AppUsageEntry>
)

fun Route.appUsageRoutes(usage: AppUsageService, rateLimiter: RateLimiter) {
    authenticate(DEVICE_AUTH) {
        post("/v1/device/app-usage") {
            val device = requireNotNull(call.principal<DevicePrincipal>())
            // Телефон шлёт снимок раз в 15 минут; 60 в час — запас на повторы после сбоя сети.
            rateLimiter.check("app-usage:${device.deviceId}", limit = 60, window = 1.hours)
            val days = call.receive<AppUsageUploadRequest>().parseAll(usage.today())
            if (days == null) {
                call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
            } else {
                usage.record(device.deviceId, days)
                call.respond(AppUsageUploadResponse(accepted = days.size))
            }
        }
    }

    authenticate(PARENT_AUTH) {
        get("/v1/devices/{deviceId}/app-usage") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            val deviceId = call.parameters["deviceId"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }
            val date = call.request.queryParameters["date"]?.toDateOrNull()
            val report = if (deviceId != null && date != null) {
                usage.day(parent.familyId, deviceId, date)
            } else {
                null
            }
            when {
                date == null ->
                    call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)

                // Чужое и несуществующее устройство неотличимы: не раскрываем, какие ID есть.
                report == null -> call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)

                else -> call.respond(report.toResponse())
            }
        }
    }
}

private fun String.toDateOrNull(): LocalDate? = try {
    LocalDate.parse(this)
} catch (ignored: DateTimeParseException) {
    null
}

/**
 * Весь снимок отклоняется, если хоть что-то в нём испорчено: это ошибка клиента.
 * Дата сверяется с сервером (UTC) с запасом в сутки вперёд — у телефона свой часовой пояс.
 */
private fun AppUsageUploadRequest.parseAll(today: LocalDate): List<DayUsage>? {
    val parsed = days.takeIf { it.size <= MAX_DAYS }?.map { it.parse(today) }
    if (parsed == null || null in parsed) return null
    return parsed.filterNotNull().takeIf { list -> list.map { it.day }.toSet().size == list.size }
}

private fun DayUsageRequest.parse(today: LocalDate): DayUsage? {
    val day = date.toDateOrNull()?.takeIf { it.isWithinWindow(today) } ?: return null
    return DayUsage(day, apps.map { AppUsage(it.packageName, it.label, it.foregroundMs) })
        .takeIf { appsAreValid() }
}

private fun LocalDate.isWithinWindow(today: LocalDate) =
    !isAfter(today.plusDays(1)) && !isBefore(today.minusDays(MAX_AGE_DAYS))

private fun DayUsageRequest.appsAreValid(): Boolean {
    val unique = apps.map { it.packageName }.toSet().size == apps.size
    return apps.size <= MAX_APPS && unique && apps.all { it.isValid() }
}

private fun AppUsageEntry.isValid() = packageName.isNotBlank() &&
    packageName.length <= MAX_PACKAGE &&
    label.isNotBlank() &&
    label.length <= MAX_LABEL &&
    foregroundMs in 0..MS_PER_DAY

private fun DayUsageReport.toResponse() = DayUsageResponse(
    date = day.toString(),
    updatedAt = updatedAt?.toString(),
    apps = apps.map { AppUsageEntry(it.packageName, it.label, it.foregroundMs) }
)

private const val MAX_DAYS = 7
private const val MAX_AGE_DAYS = 7L
private const val MAX_APPS = 300
private const val MAX_PACKAGE = 255
private const val MAX_LABEL = 100
private const val MS_PER_DAY = 24L * 60 * 60 * 1000
