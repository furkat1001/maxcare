package uz.maxcare.backend.filterwords

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
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
data class FilterWordsResponse(val words: List<String>)

@Serializable
data class FilterWordRequest(val word: String)

@Serializable
data class SettingsResponse(val strictness: String)

@Serializable
data class SettingsRequest(val strictness: String)

@Serializable
data class FilterConfigResponse(
    val block: List<String>,
    val allow: List<String>,
    val strictness: String
)

fun Route.filterWordsRoutes(service: FilterWordsService, rateLimiter: RateLimiter) {
    authenticate(PARENT_AUTH) {
        // kind=block (по умолчанию) — стоп-слова, kind=allow — разрешённые.
        get("/v1/filter-words") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            val kind = kindOf(call.request.queryParameters["kind"])
            call.respond(FilterWordsResponse(service.list(parent.familyId, kind)))
        }

        post("/v1/filter-words") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            rateLimiter.check("filterwords:${parent.familyId}", limit = 120, window = 1.hours)
            val kind = kindOf(call.request.queryParameters["kind"])
            val word = call.receive<FilterWordRequest>().word
            when (val result = service.add(parent.familyId, word, kind)) {
                is AddResult.Added ->
                    call.respond(HttpStatusCode.Created, FilterWordRequest(result.word))

                AddResult.Invalid ->
                    call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)

                AddResult.LimitReached ->
                    call.respondError(HttpStatusCode.Conflict, ErrorCodes.LIMIT_REACHED)
            }
        }

        delete("/v1/filter-words") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            val kind = kindOf(call.request.queryParameters["kind"])
            val word = call.request.queryParameters["word"].orEmpty()
            if (service.remove(parent.familyId, word, kind)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)
            }
        }

        get("/v1/settings") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            call.respond(SettingsResponse(service.strictness(parent.familyId).wire))
        }

        put("/v1/settings") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            val value = Strictness.parse(call.receive<SettingsRequest>().strictness)
            if (value == null) {
                call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
            } else {
                service.setStrictness(parent.familyId, value)
                call.respond(SettingsResponse(value.wire))
            }
        }
    }

    authenticate(DEVICE_AUTH) {
        get("/v1/device/filter-config") {
            val device = requireNotNull(call.principal<DevicePrincipal>())
            val config = service.deviceConfig(device.deviceId)
            if (config == null) {
                call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)
            } else {
                call.respond(FilterConfigResponse(config.block, config.allow, config.strictness))
            }
        }
    }
}

/** Неизвестный kind трактуем как block — родитель присылает только корректные значения. */
private fun kindOf(value: String?): WordKind = WordKind.parse(value) ?: WordKind.BLOCK
