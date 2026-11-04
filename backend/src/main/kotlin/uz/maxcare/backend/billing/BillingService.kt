package uz.maxcare.backend.billing

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.db.update

/** Что оплачено в магазине; [expiresAt] null — бессрочно. */
data class StoreEntitlement(val expiresAt: Instant?, val productId: String?)

/** Источник правды об оплате; в тестах — подделка без сети. */
fun interface EntitlementSource {
    /** @return null — подписку ни разу не покупали. */
    suspend fun entitlement(appUserId: String): StoreEntitlement?
}

/**
 * Подписка семьи. ID пользователя в RevenueCat — ID семьи: подписку покупает любой родитель,
 * действует она для всех. Дата оплаты берётся только из RevenueCat, не из приложения и не из
 * тела уведомления: подделать оплату нельзя.
 * @param source null — оплата не настроена (нет ключа RevenueCat): у всех пробный период.
 */
class BillingService(
    private val db: Db,
    private val source: EntitlementSource?,
    private val clock: Clock
) {
    /** @return false — оплата не настроена или такой семьи нет. */
    suspend fun sync(familyId: UUID): Boolean {
        val store = source ?: return false
        val entitlement = store.entitlement(familyId.toString())
        val paidUntil = when {
            entitlement == null -> null
            entitlement.expiresAt == null -> LIFETIME
            else -> entitlement.expiresAt
        }
        return db.tx { c ->
            c.update(
                """
                update families set paid_until = ?, paid_product = ?, billing_synced_at = ?
                where id = ?
                """.trimIndent(),
                paidUntil,
                entitlement?.productId,
                clock.instant(),
                familyId
            ) == 1
        }
    }

    private companion object {
        val LIFETIME: Instant = Instant.parse("9999-12-31T00:00:00Z")
    }
}

/**
 * RevenueCat REST API v1: `GET /subscribers/{id}` с секретным ключом. Ключ — только на сервере.
 * Ошибка сети или ответа — исключение: уведомление вернёт 500, и RevenueCat повторит его.
 */
class RevenueCatEntitlements(
    private val apiKey: String,
    private val entitlementId: String,
    private val http: HttpClient = HttpClient.newHttpClient(),
    private val baseUrl: String = "https://api.revenuecat.com/v1"
) : EntitlementSource {
    override suspend fun entitlement(appUserId: String): StoreEntitlement? {
        val id = URLEncoder.encode(appUserId, Charsets.UTF_8)
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/subscribers/$id"))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .timeout(java.time.Duration.ofSeconds(TIMEOUT_SECONDS))
            .GET()
            .build()
        val response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        if (response.statusCode() !in
            SUCCESS
        ) {
            throw IOException("RevenueCat ${response.statusCode()}")
        }
        return parseEntitlement(response.body(), entitlementId)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        val SUCCESS = 200..299
    }
}

/** Разбор ответа RevenueCat; вынесен ради тестов без сети. */
internal fun parseEntitlement(body: String, entitlementId: String): StoreEntitlement? {
    val subscriber = Json.parseToJsonElement(body).jsonObject["subscriber"]?.jsonObject
    val entitlement =
        (subscriber?.get("entitlements") as? JsonObject)?.get(entitlementId)?.jsonObject
            ?: return null
    val expires = entitlement["expires_date"]?.jsonPrimitive?.takeIf { it.isString }?.content
    val product = entitlement["product_identifier"]?.jsonPrimitive?.takeIf { it.isString }?.content
    return StoreEntitlement(expires?.let(Instant::parse), product)
}
