package uz.maxcare.backend

import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uz.maxcare.backend.parents.AccountResponse
import uz.maxcare.backend.parents.trialDaysLeft

class AccountApiTest : ApiTestBase() {

    @Test
    fun `a new account shows its e-mail and a 14 day trial`() = apiTest { client ->
        val email = newEmail()
        val token = client.registerAndLogin(email)

        val account = client.getJson("/v1/account", token).body<AccountResponse>()

        assertEquals(email, account.email)
        assertTrue(account.hasPassword)
        assertEquals(emptyList(), account.providers)
        assertEquals("trial", account.plan.status)
        assertEquals(14, account.plan.trialDaysLeft)
        assertEquals("2026-10-23T10:00:00Z", account.plan.trialEndsAt)
    }

    @Test
    fun `the trial counts down and then expires`() = apiTest { client ->
        val token = client.registerAndLogin()

        clock.now = clock.now.plus(Duration.ofDays(10).plusHours(1))
        val later = client.getJson("/v1/account", token).body<AccountResponse>()
        assertEquals(4, later.plan.trialDaysLeft)

        clock.now = clock.now.plus(Duration.ofDays(5))
        val expired = client.getJson("/v1/account", token).body<AccountResponse>()
        assertEquals("expired", expired.plan.status)
        assertEquals(0, expired.plan.trialDaysLeft)
    }

    @Test
    fun `the account needs a parent session`() = apiTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.getJson("/v1/account").status)
        assertEquals(HttpStatusCode.Unauthorized, client.getJson("/v1/account", "nope").status)
    }

    @Test
    fun `a part of a day left counts as a whole day`() {
        val end = Instant.parse("2026-10-23T10:00:00Z")

        assertEquals(1, trialDaysLeft(end.minusSeconds(1), end))
        assertEquals(2, trialDaysLeft(end.minus(Duration.ofHours(25)), end))
        assertEquals(0, trialDaysLeft(end, end))
        assertEquals(0, trialDaysLeft(end.plusSeconds(60), end))
    }
}
