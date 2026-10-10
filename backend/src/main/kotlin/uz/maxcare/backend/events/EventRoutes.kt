package uz.maxcare.backend.events

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.Serializable
import uz.maxcare.backend.devices.DevicePrincipal
import uz.maxcare.backend.devices.PARENT_AUTH
import uz.maxcare.backend.http.ErrorCodes
import uz.maxcare.backend.http.respondError
import uz.maxcare.backend.parents.ParentPrincipal
import uz.maxcare.backend.ratelimit.RateLimiter
import uz.maxcare.backend.ratelimit.check

const val DEVICE_AUTH = "device"

@Serializable
data class EventDto(
    val id: String,
    val type: String,
    val occurredAt: String,
    val detail: String? = null
)

@Serializable
data class EventBatchRequest(val events: List<EventDto> = emptyList())

@Serializable
data class EventBatchResponse(val accepted: Int)

@Serializable
data class DeviceEventDto(
    val type: String,
    val occurredAt: String,
    val receivedAt: String,
    val detail: String? = null
)

@Serializable
data class DeviceActivityResponse(val lastSeenAt: String?, val events: List<DeviceEventDto>)

fun Route.eventRoutes(events: EventService, rateLimiter: RateLimiter) {
    authenticate(DEVICE_AUTH) {
        post("/v1/device/events") {
            val device = requireNotNull(call.principal<DevicePrincipal>())
            // Телефон отмечается раз в 15 минут и после событий; 120 в час — с большим запасом.
            rateLimiter.check("events:${device.deviceId}", limit = 120, window = 1.hours)
            val request = call.receive<EventBatchRequest>()
            val parsed = parseBatch(request)
            if (parsed == null) {
                call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
            } else {
                call.respond(EventBatchResponse(events.record(device.deviceId, parsed)))
            }
        }
    }

    authenticate(PARENT_AUTH) {
        get("/v1/devices/{deviceId}/events") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            val deviceId = call.parameters["deviceId"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }
            val activity = deviceId?.let { events.activity(parent.familyId, it) }
            if (activity == null) {
                call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)
            } else {
                call.respond(activity.toResponse())
            }
        }
    }
}

/**
 * null — пачка испорчена (неверный id или время): это ошибка клиента.
 * Неизвестный тип события пропускается, а не отклоняет пачку: иначе новая версия приложения
 * со старым сервером застряла бы, бесконечно повторяя одну и ту же отправку.
 * Время из будущего принимается как есть — рядом хранится время сервера.
 */
private fun parseBatch(request: EventBatchRequest): List<IncomingEvent>? {
    if (request.events.size > MAX_BATCH) return null
    return try {
        request.events.mapNotNull { dto ->
            val id = UUID.fromString(dto.id)
            val occurredAt = Instant.parse(dto.occurredAt)
            EventType.parse(dto.type)?.let {
                IncomingEvent(id, it, occurredAt, dto.detail?.takeIf(::isPlainDomain))
            }
        }
    } catch (ignored: IllegalArgumentException) {
        null
    } catch (ignored: DateTimeParseException) {
        null
    }
}

private fun DeviceActivity.toResponse() = DeviceActivityResponse(
    lastSeenAt = lastSeenAt?.toString(),
    events = events.map {
        DeviceEventDto(
            it.type.wireName,
            it.occurredAt.toString(),
            it.receivedAt.toString(),
            it.detail
        )
    }
)

/**
 * Уточнение принимается только как обычное доменное имя (адрес заблокированного сайта).
 * Иное отбрасывается, а не отклоняет пачку, — по той же причине, что и неизвестный тип события.
 */
private fun isPlainDomain(value: String) =
    value.length <= MAX_DOMAIN_LENGTH && DOMAIN.matches(value)

private val DOMAIN = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")
private const val MAX_DOMAIN_LENGTH = 253
private const val MAX_BATCH = 100
