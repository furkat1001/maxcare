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

fun Route.filterWordsRoutes(service: FilterWordsService, rateLimiter: RateLimiter) {
    authenticate(PARENT_AUTH) {
        get("/v1/filter-words") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            call.respond(FilterWordsResponse(service.list(parent.familyId)))
        }

        post("/v1/filter-words") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            rateLimiter.check("filterwords:${parent.familyId}", limit = 120, window = 1.hours)
            val word = call.receive<FilterWordRequest>().word
            when (val result = service.add(parent.familyId, word)) {
                is AddResult.Added ->
                    call.respond(HttpStatusCode.Created, FilterWordRequest(result.word))

                AddResult.Invalid ->
                    call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)

                AddResult.LimitReached ->
                    call.respondError(HttpStatusCode.Conflict, ErrorCodes.LIMIT_REACHED)
            }
        }

        // Слово — в query, а не в пути: у стоп-слов бывают символы, неудобные в URL-пути.
        delete("/v1/filter-words") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            val word = call.request.queryParameters["word"].orEmpty()
            if (service.remove(parent.familyId, word)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)
            }
        }
    }

    authenticate(DEVICE_AUTH) {
        get("/v1/device/filter-words") {
            val device = requireNotNull(call.principal<DevicePrincipal>())
            val words = service.forDevice(device.deviceId)
            if (words == null) {
                call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)
            } else {
                call.respond(FilterWordsResponse(words))
            }
        }
    }
}
