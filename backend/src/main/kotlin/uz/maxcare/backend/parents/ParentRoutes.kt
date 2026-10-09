package uz.maxcare.backend.parents

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import uz.maxcare.backend.http.ErrorCodes
import uz.maxcare.backend.http.clientIp
import uz.maxcare.backend.http.respondError
import uz.maxcare.backend.ratelimit.RateLimiter
import uz.maxcare.backend.ratelimit.check

@Serializable
data class CredentialsRequest(val email: String, val password: String)

@Serializable
data class RegisterResponse(val parentId: String)

@Serializable
data class SessionResponse(val token: String)

fun Route.parentRoutes(parents: ParentService, rateLimiter: RateLimiter) {
    post("/v1/parents") {
        rateLimiter.check("register:${call.clientIp}", limit = 5, window = 1.hours)
        val request = call.receive<CredentialsRequest>()
        when (val result = parents.register(request.email, request.password)) {
            is RegisterResult.Created ->
                call.respond(HttpStatusCode.Created, RegisterResponse(result.parentId.toString()))

            RegisterResult.EmailTaken -> call.respondError(
                HttpStatusCode.Conflict,
                ErrorCodes.EMAIL_TAKEN
            )

            RegisterResult.Invalid -> call.respondError(
                HttpStatusCode.BadRequest,
                ErrorCodes.INVALID_REQUEST
            )
        }
    }

    post("/v1/sessions") {
        val request = call.receive<CredentialsRequest>()
        // Лимит на пару «адрес + e-mail»: перебор паролей одного родителя.
        rateLimiter.check(
            "login:${call.clientIp}:${request.email.trim().lowercase()}",
            limit = 10,
            window = 15.minutes
        )
        val token = parents.login(request.email, request.password)
        if (token == null) {
            call.respondError(HttpStatusCode.Unauthorized, ErrorCodes.INVALID_CREDENTIALS)
        } else {
            call.respond(HttpStatusCode.Created, SessionResponse(token))
        }
    }
}
