package uz.maxcare.backend.location

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
import uz.maxcare.backend.events.DEVICE_AUTH
import uz.maxcare.backend.http.ErrorCodes
import uz.maxcare.backend.http.respondError
import uz.maxcare.backend.parents.ParentPrincipal
import uz.maxcare.backend.ratelimit.RateLimiter
import uz.maxcare.backend.ratelimit.check

@Serializable
data class LocationReportRequest(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null,
    val recordedAt: String
)

@Serializable
data class LocationBatchRequest(val locations: List<LocationReportRequest> = emptyList())

@Serializable
data class LocationBatchResponse(val accepted: Int)

@Serializable
data class DeviceLocationResponse(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null,
    val recordedAt: String,
    val receivedAt: String
)

fun Route.locationRoutes(locations: LocationService, rateLimiter: RateLimiter) {
    deviceLocationRoutes(locations, rateLimiter)
    parentLocationRoutes(locations)
}

private fun Route.deviceLocationRoutes(locations: LocationService, rateLimiter: RateLimiter) {
    authenticate(DEVICE_AUTH) {
        post("/v1/device/location") {
            val device = requireNotNull(call.principal<DevicePrincipal>())
            // Телефон шлёт позицию при заметном перемещении; 120 в час — с большим запасом.
            rateLimiter.check("location:${device.deviceId}", limit = 120, window = 1.hours)
            val incoming = call.receive<LocationReportRequest>().parse()
            if (incoming == null) {
                call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
            } else {
                locations.record(device.deviceId, incoming)
                call.respond(HttpStatusCode.Accepted)
            }
        }

        post("/v1/device/locations") {
            val device = requireNotNull(call.principal<DevicePrincipal>())
            rateLimiter.check("location:${device.deviceId}", limit = 120, window = 1.hours)
            val batch = call.receive<LocationBatchRequest>().parseAll()
            if (batch == null) {
                call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
            } else {
                call.respond(LocationBatchResponse(locations.record(device.deviceId, batch)))
            }
        }
    }
}

private fun Route.parentLocationRoutes(locations: LocationService) {
    authenticate(PARENT_AUTH) {
        get("/v1/devices/{deviceId}/location") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            val deviceId = call.parameters["deviceId"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }
            val result = deviceId?.let { locations.latest(parent.familyId, it) }
            val location = (result as? LocationResult.Found)?.location
            when {
                result !is LocationResult.Found ->
                    // Чужое и несуществующее устройство неотличимы: не раскрываем, какие ID есть.
                    call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)

                // Устройство семьи есть, но телефон ещё не слал позицию.
                location == null -> call.respond(HttpStatusCode.NoContent)

                else -> call.respond(location.toResponse())
            }
        }

        get("/v1/devices/{deviceId}/locations") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            val deviceId = call.parameters["deviceId"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }
            val sinceParam = call.request.queryParameters["since"]
            val since = sinceParam?.let { runCatching { Instant.parse(it) }.getOrNull() }
            when {
                deviceId == null || (sinceParam != null && since == null) ->
                    call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)

                else -> {
                    val track = locations.history(parent.familyId, deviceId, since)
                    if (track == null) {
                        call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)
                    } else {
                        call.respond(track.map { it.toResponse() })
                    }
                }
            }
        }
    }
}

private fun LocationReportRequest.parse(): IncomingLocation? {
    val validAccuracy = accuracyMeters == null || (accuracyMeters >= 0 && accuracyMeters.isFinite())
    if (latitude !in LAT_RANGE || longitude !in LON_RANGE || !validAccuracy) return null
    return try {
        IncomingLocation(
            id = UUID.fromString(id),
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            recordedAt = Instant.parse(recordedAt)
        )
    } catch (ignored: IllegalArgumentException) {
        null
    } catch (ignored: DateTimeParseException) {
        null
    }
}

private fun DeviceLocation.toResponse() = DeviceLocationResponse(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    recordedAt = recordedAt.toString(),
    receivedAt = receivedAt.toString()
)

// Вся пачка отклоняется, если хоть одна точка испорчена: это ошибка клиента, а не «часть потерять».
private fun LocationBatchRequest.parseAll(): List<IncomingLocation>? {
    if (locations.size > MAX_BATCH) return null
    val parsed = locations.mapNotNull { it.parse() }
    return if (parsed.size == locations.size) parsed else null
}

private val LAT_RANGE = -90.0..90.0
private val LON_RANGE = -180.0..180.0
private const val MAX_BATCH = 100
