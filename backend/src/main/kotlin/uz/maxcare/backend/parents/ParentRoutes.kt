package uz.maxcare.backend.parents

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import uz.maxcare.backend.http.ErrorCodes
import uz.maxcare.backend.http.clientIp
import uz.maxcare.backend.http.respondError
import uz.maxcare.backend.ratelimit.RateLimiter
import uz.maxcare.backend.ratelimit.check
import uz.maxcare.backend.security.IdTokenVerifier
import uz.maxcare.backend.security.IdentityProvider

@Serializable
data class CredentialsRequest(val email: String, val password: String)

@Serializable
data class RegisterResponse(val parentId: String)

@Serializable
data class SessionResponse(val token: String)

@Serializable
data class IdTokenRequest(val idToken: String)

/** @param idTokens проверка ID-токенов по провайдерам; провайдера нет в карте — вход через него выключен. */
fun Route.parentRoutes(
    parents: ParentService,
    idTokens: Map<IdentityProvider, IdTokenVerifier>,
    rateLimiter: RateLimiter
) {
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

    // Вход через Google / Apple: приложение присылает ID-токен провайдера, сервер проверяет его сам.
    post("/v1/sessions/{provider}") {
        rateLimiter.check("id-login:${call.clientIp}", limit = 20, window = 15.minutes)
        val verifier = IdentityProvider.parse(call.parameters["provider"])?.let(idTokens::get)
            ?: return@post call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)
        val request = call.receive<IdTokenRequest>()
        val identity = withContext(Dispatchers.IO) { verifier.verify(request.idToken) }
            ?: return@post call.respondError(
                HttpStatusCode.Unauthorized,
                ErrorCodes.INVALID_CREDENTIALS
            )
        when (val result = parents.loginWithIdentity(identity)) {
            is IdentityLoginResult.Session ->
                call.respond(HttpStatusCode.Created, SessionResponse(result.token))

            IdentityLoginResult.EmailTaken ->
                call.respondError(HttpStatusCode.Conflict, ErrorCodes.EMAIL_TAKEN)

            IdentityLoginResult.NoEmail ->
                call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
        }
    }
}
