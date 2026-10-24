package uz.maxcare.backend.reports

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID
import uz.maxcare.backend.devices.PARENT_AUTH
import uz.maxcare.backend.http.ErrorCodes
import uz.maxcare.backend.http.respondError
import uz.maxcare.backend.parents.ParentPrincipal

/**
 * `GET /v1/devices/{deviceId}/daily-report?date=2026-10-23&offsetMinutes=300` — итоги дня.
 * [offsetMinutes] — смещение часового пояса родителя от UTC (Ташкент: 300), чтобы «день»
 * совпадал с его календарём.
 */
fun Route.dailyReportRoutes(reports: DailyReportService) {
    authenticate(PARENT_AUTH) {
        get("/v1/devices/{deviceId}/daily-report") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            val deviceId = call.parameters["deviceId"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }
            val day = call.request.queryParameters["date"]?.toDateOrNull()
            val offset = call.request.queryParameters["offsetMinutes"].toOffsetOrNull()
            if (day == null || offset == null) {
                return@get call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
            }
            val report = deviceId?.let { reports.report(parent.familyId, it, day, offset) }
            if (report == null) {
                // Чужое и несуществующее устройство неотличимы: не раскрываем, какие ID есть.
                call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)
            } else {
                call.respond(report)
            }
        }
    }
}

private fun String.toDateOrNull(): LocalDate? = try {
    LocalDate.parse(this)
} catch (ignored: DateTimeParseException) {
    null
}

/** Без параметра — UTC; допустимы пояса от −12:00 до +14:00. */
private fun String?.toOffsetOrNull(): ZoneOffset? {
    val minutes = if (this == null) 0 else toIntOrNull() ?: return null
    return if (minutes in MIN_OFFSET_MIN..MAX_OFFSET_MIN) {
        ZoneOffset.ofTotalSeconds(minutes * SECONDS_PER_MINUTE)
    } else {
        null
    }
}

private const val MIN_OFFSET_MIN = -12 * 60
private const val MAX_OFFSET_MIN = 14 * 60
private const val SECONDS_PER_MINUTE = 60
