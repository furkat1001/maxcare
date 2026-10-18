package uz.maxcare.backend.devices

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.Base64
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import uz.maxcare.backend.http.ErrorCodes
import uz.maxcare.backend.http.clientIp
import uz.maxcare.backend.http.respondError
import uz.maxcare.backend.parents.ParentPrincipal
import uz.maxcare.backend.ratelimit.RateLimiter
import uz.maxcare.backend.ratelimit.check

const val PARENT_AUTH = "parent"

@Serializable
data class PairingCodeRequest(val childName: String? = null)

@Serializable
data class PairingCodeResponse(val code: String, val expiresAt: String)

@Serializable
data class PairRequest(val code: String, val deviceName: String, val platform: String)

@Serializable
data class PairResponse(val deviceId: String, val deviceToken: String, val recoverySecret: String)

@Serializable
data class RecoveryCodeResponse(val code: String, val validUntil: String)

@Serializable
data class DeviceSummaryDto(
    val id: String,
    val name: String,
    val childName: String? = null,
    val platform: String,
    val lastSeenAt: String? = null
)

fun Route.deviceRoutes(devices: DeviceService, rateLimiter: RateLimiter) {
    // Телефон ребёнка ещё не имеет токена: защита от подбора кода — только лимит по адресу.
    post("/v1/devices/pair") {
        rateLimiter.check("pair:${call.clientIp}", limit = 10, window = 10.minutes)
        val request = call.receive<PairRequest>()
        val platform = Platform.parse(request.platform)
        if (platform == null || request.deviceName.isBlank() ||
            request.deviceName.length > MAX_NAME_LENGTH
        ) {
            return@post call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
        }
        when (val result = devices.pair(request.code.trim(), request.deviceName.trim(), platform)) {
            is PairResult.Paired -> call.respond(
                HttpStatusCode.Created,
                PairResponse(
                    deviceId = result.deviceId.toString(),
                    deviceToken = result.deviceToken,
                    recoverySecret = Base64.getEncoder().encodeToString(result.recoverySecret)
                )
            )

            PairResult.InvalidCode -> call.respondError(
                HttpStatusCode.BadRequest,
                ErrorCodes.INVALID_CODE
            )
        }
    }

    authenticate(PARENT_AUTH) {
        get("/v1/devices") {
            val parent = call.parent()
            call.respond(devices.listDevices(parent.familyId).map(::toDeviceSummaryDto))
        }

        post("/v1/pairing-codes") { createPairingCode(call, devices, rateLimiter) }

        post("/v1/devices/{deviceId}/recovery-code") {
            val parent = call.parent()
            rateLimiter.check("recovery-code:${parent.parentId}", limit = 10, window = 1.hours)
            val deviceId = call.parameters["deviceId"]?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }
            val code = deviceId?.let { devices.recoveryCode(parent.familyId, it) }
            if (code == null) {
                // Чужое и несуществующее устройство неотличимы: не раскрываем, какие ID существуют.
                call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)
            } else {
                call.respond(RecoveryCodeResponse(code.code, code.validUntil.toString()))
            }
        }
    }
}

private suspend fun createPairingCode(
    call: RoutingCall,
    devices: DeviceService,
    rateLimiter: RateLimiter
) {
    val parent = call.parent()
    rateLimiter.check("pairing-code:${parent.parentId}", limit = 20, window = 1.hours)
    val request = call.receive<PairingCodeRequest>()
    val childName = request.childName?.trim()?.takeIf { it.isNotEmpty() }
    if (childName != null && childName.length > MAX_NAME_LENGTH) {
        return call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
    }
    when (val result = devices.createPairingCode(parent.familyId, childName)) {
        is PairingCodeResult.Created -> call.respond(
            HttpStatusCode.Created,
            PairingCodeResponse(result.code.code, result.code.expiresAt.toString())
        )

        PairingCodeResult.LimitReached -> call.respondError(
            HttpStatusCode.Conflict,
            ErrorCodes.CHILD_LIMIT
        )
    }
}

private fun RoutingCall.parent(): ParentPrincipal = requireNotNull(principal<ParentPrincipal>()) {
    "Route must be inside authenticate(PARENT_AUTH)"
}

private fun toDeviceSummaryDto(device: DeviceSummary) = DeviceSummaryDto(
    id = device.id.toString(),
    name = device.name,
    childName = device.childName,
    platform = device.platform,
    lastSeenAt = device.lastSeenAt?.toString()
)

private const val MAX_NAME_LENGTH = 64
