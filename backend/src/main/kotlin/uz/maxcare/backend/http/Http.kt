package uz.maxcare.backend.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import uz.maxcare.backend.ratelimit.RateLimitedException

/** Тело ответа с ошибкой: машинный код, который приложения переводят в текст на нужном языке. */
@Serializable
data class ApiError(val error: String)

object ErrorCodes {
    const val INVALID_REQUEST = "invalid_request"
    const val EMAIL_TAKEN = "email_taken"
    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val INVALID_CODE = "invalid_code"
    const val NOT_FOUND = "not_found"
    const val RATE_LIMITED = "rate_limited"
    const val UNAUTHORIZED = "unauthorized"
    const val INTERNAL = "internal_error"
}

suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String) =
    respond(status, ApiError(code))

/**
 * Адрес клиента для лимитов. За обратным прокси здесь окажется адрес прокси —
 * при развёртывании включить разбор X-Forwarded-For только от доверенного прокси.
 */
val ApplicationCall.clientIp: String get() = request.origin.remoteHost

fun Application.installErrorHandling() {
    val log = LoggerFactory.getLogger("uz.maxcare.backend.http")
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)
        }
        exception<RateLimitedException> { call, _ ->
            call.respondError(HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED)
        }
        exception<Throwable> { call, cause ->
            // Только тип и путь: в сообщениях исключений могут оказаться данные запроса.
            log.error("Unhandled {} on {}", cause::class.qualifiedName, call.request.local.uri)
            call.respondError(HttpStatusCode.InternalServerError, ErrorCodes.INTERNAL)
        }
        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respondError(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED)
        }
    }
}
