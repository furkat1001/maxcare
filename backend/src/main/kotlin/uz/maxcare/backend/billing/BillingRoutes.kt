package uz.maxcare.backend.billing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uz.maxcare.backend.devices.PARENT_AUTH
import uz.maxcare.backend.http.ErrorCodes
import uz.maxcare.backend.http.respondError
import uz.maxcare.backend.parents.ParentPrincipal
import uz.maxcare.backend.ratelimit.RateLimiter
import uz.maxcare.backend.ratelimit.check

/**
 * @param webhookAuth значение заголовка `Authorization`, заданное в настройках уведомлений
 *   RevenueCat; null — уведомления выключены.
 */
fun Route.billingRoutes(billing: BillingService, webhookAuth: String?, rateLimiter: RateLimiter) {
    // Уведомление RevenueCat о покупке, продлении, отмене или окончании. Из тела берётся только
    // ID семьи: что оплачено, сервер спрашивает у RevenueCat сам.
    post("/v1/billing/revenuecat") {
        val auth = call.request.headers["Authorization"]
        if (webhookAuth == null || auth == null || !sameSecret(auth, webhookAuth)) {
            return@post call.respondError(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED)
        }
        // Чужой ID (например, тестовое уведомление из кабинета) — не ошибка: иначе RevenueCat
        // повторял бы его бесконечно.
        appUserId(call.receiveText())?.let { billing.sync(it) }
        call.respond(HttpStatusCode.OK)
    }

    authenticate(PARENT_AUTH) {
        // Сразу после покупки: уведомление может прийти позже, а родитель ждёт «Оплачено».
        post("/v1/account/billing/sync") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            rateLimiter.check("billing-sync:${parent.familyId}", limit = 30, window = 1.hours)
            billing.sync(parent.familyId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun appUserId(body: String): UUID? = try {
    val event = Json.parseToJsonElement(body).jsonObject["event"]?.jsonObject
    event?.get("app_user_id")?.jsonPrimitive?.content?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    }
} catch (ignored: SerializationException) {
    null
} catch (ignored: IllegalArgumentException) {
    null
}

// Сравнение за постоянное время: по времени ответа секрет не подобрать.
private fun sameSecret(given: String, expected: String) =
    MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())
