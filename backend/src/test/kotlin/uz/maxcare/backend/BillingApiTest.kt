package uz.maxcare.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import uz.maxcare.backend.billing.BillingService
import uz.maxcare.backend.billing.EntitlementSource
import uz.maxcare.backend.billing.StoreEntitlement
import uz.maxcare.backend.billing.parseEntitlement
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.parents.AccountResponse

class BillingApiTest : ApiTestBase() {

    @BeforeEach
    fun clearStore() = TestInfra.entitlements.clear()

    private suspend fun HttpClient.account(token: String) =
        getJson("/v1/account", token).body<AccountResponse>()

    private suspend fun HttpClient.webhook(
        appUserId: String,
        auth: String? = TestInfra.REVENUECAT_WEBHOOK_AUTH
    ) = post("/v1/billing/revenuecat") {
        auth?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(
            """{"api_version":"1.0","event":{"type":"INITIAL_PURCHASE","app_user_id":"$appUserId"}}"""
        )
    }

    private fun paidFor(familyId: String, until: Instant) {
        TestInfra.entitlements[familyId] = StoreEntitlement(until, "maxcare_monthly")
    }

    @Test
    fun `the account tells the app which family to buy for`() = apiTest { client ->
        val account = client.account(client.registerAndLogin())

        assertEquals(36, account.familyId?.length)
        assertNull(account.plan.paidUntil)
    }

    @Test
    fun `a revenuecat notification makes the plan active`() = apiTest { client ->
        val token = client.registerAndLogin()
        val familyId = requireNotNull(client.account(token).familyId)
        paidFor(familyId, Instant.parse("2026-11-09T10:00:00Z"))

        assertEquals(HttpStatusCode.OK, client.webhook(familyId).status)

        val plan = client.account(token).plan
        assertEquals("active", plan.status)
        assertEquals("2026-11-09T10:00:00Z", plan.paidUntil)
        assertEquals("2026-11-12T10:00:00Z", plan.graceEndsAt)
    }

    @Test
    fun `notifications need the secret from the revenuecat settings`() = apiTest { client ->
        val token = client.registerAndLogin()
        val familyId = requireNotNull(client.account(token).familyId)
        paidFor(familyId, Instant.parse("2026-12-09T10:00:00Z"))

        assertEquals(HttpStatusCode.Unauthorized, client.webhook(familyId, auth = null).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.webhook(familyId, auth = "Bearer guess").status
        )
        assertEquals("trial", client.account(token).plan.status)
    }

    @Test
    fun `the notification body cannot fake a payment`() = apiTest { client ->
        // Сервер верит только RevenueCat: в «магазине» ничего не куплено.
        val token = client.registerAndLogin()
        val familyId = requireNotNull(client.account(token).familyId)

        client.webhook(familyId)

        assertNull(client.account(token).plan.paidUntil)
    }

    @Test
    fun `unknown users in notifications are acknowledged and ignored`() = apiTest { client ->
        assertEquals(HttpStatusCode.OK, client.webhook("\$RCAnonymousID:test").status)
        assertEquals(
            HttpStatusCode.OK,
            client.webhook("00000000-0000-0000-0000-000000000000").status
        )
    }

    @Test
    fun `the app can ask for a sync right after a purchase`() = apiTest { client ->
        val token = client.registerAndLogin()
        val familyId = requireNotNull(client.account(token).familyId)
        paidFor(familyId, Instant.parse("2027-10-09T10:00:00Z"))

        val response = client.postJson("/v1/account/billing/sync", mapOf<String, String>(), token)

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("active", client.account(token).plan.status)
    }

    @Test
    fun `sync needs a parent session`() = apiTest { client ->
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.postJson("/v1/account/billing/sync", mapOf<String, String>()).status
        )
    }

    @Test
    fun `an ended subscription gets three days of grace and then expires`() = apiTest { client ->
        val token = client.registerAndLogin()
        val familyId = requireNotNull(client.account(token).familyId)
        val paidUntil = Instant.parse("2026-10-30T10:00:00Z")
        paidFor(familyId, paidUntil)
        client.webhook(familyId)

        clock.now = paidUntil.plus(Duration.ofDays(1))
        val grace = client.account(token).plan
        assertEquals("grace", grace.status)
        assertEquals("2026-11-02T10:00:00Z", grace.graceEndsAt)

        clock.now = paidUntil.plus(Duration.ofDays(3))
        assertEquals("expired", client.account(token).plan.status)
    }

    @Test
    fun `a family pays for itself only`() = apiTest { client ->
        val payer = client.registerAndLogin()
        val other = client.registerAndLogin()
        val familyId = requireNotNull(client.account(payer).familyId)
        paidFor(familyId, Instant.parse("2026-12-09T10:00:00Z"))

        client.webhook(familyId)

        assertEquals("active", client.account(payer).plan.status)
        assertEquals("trial", client.account(other).plan.status)
    }

    @Test
    fun `a store error is reported so revenuecat retries`() = runBlocking {
        val failing =
            BillingService(
                Db(TestInfra.dataSource),
                EntitlementSource {
                    error("RevenueCat down")
                },
                clock
            )
        val result = runCatching { failing.sync(java.util.UUID.randomUUID()) }
        assertEquals("RevenueCat down", result.exceptionOrNull()?.message)
    }

    @Test
    fun `revenuecat subscriber answer is read`() {
        val body = """
            {"subscriber":{"entitlements":{
              "pro":{"expires_date":"2026-12-04T10:00:00Z","product_identifier":"maxcare_yearly"},
              "other":{"expires_date":null,"product_identifier":"x"}
            }}}
        """.trimIndent()

        assertEquals(
            StoreEntitlement(Instant.parse("2026-12-04T10:00:00Z"), "maxcare_yearly"),
            parseEntitlement(body, "pro")
        )
        assertEquals(StoreEntitlement(null, "x"), parseEntitlement(body, "other"))
        assertNull(parseEntitlement("""{"subscriber":{"entitlements":{}}}""", "pro"))
    }
}
