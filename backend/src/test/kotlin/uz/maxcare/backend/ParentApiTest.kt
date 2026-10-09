package uz.maxcare.backend

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.http.ApiError
import uz.maxcare.backend.parents.CredentialsRequest

class ParentApiTest : ApiTestBase() {

    @Test
    fun `health check answers`() = apiTest { client ->
        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun `parent registers and logs in`() = apiTest { client ->
        val token = client.registerAndLogin()

        assertTrue(token.length >= 40)
    }

    @Test
    fun `same e-mail cannot register twice, regardless of case`() = apiTest { client ->
        val email = newEmail()
        client.postJson("/v1/parents", CredentialsRequest(email, PASSWORD))

        val second = client.postJson("/v1/parents", CredentialsRequest(email.uppercase(), PASSWORD))

        assertEquals(HttpStatusCode.Conflict, second.status)
        assertEquals("email_taken", second.body<ApiError>().error)
    }

    @Test
    fun `invalid e-mail or short password is rejected`() = apiTest { client ->
        listOf(
            CredentialsRequest("not-an-email", PASSWORD),
            CredentialsRequest(newEmail(), "short")
        ).forEach { request ->
            assertEquals(HttpStatusCode.BadRequest, client.postJson("/v1/parents", request).status)
        }
    }

    @Test
    fun `malformed json is a bad request, not a server error`() = apiTest { client ->
        val response = client.post("/v1/parents") {
            contentType(ContentType.Application.Json)
            setBody("{not json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `wrong password and unknown e-mail look the same`() = apiTest { client ->
        val email = newEmail()
        client.postJson("/v1/parents", CredentialsRequest(email, PASSWORD))

        val wrongPassword = client.postJson(
            "/v1/sessions",
            CredentialsRequest(email, "wrong-password")
        )
        val unknownEmail = client.postJson("/v1/sessions", CredentialsRequest(newEmail(), PASSWORD))

        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(HttpStatusCode.Unauthorized, unknownEmail.status)
        assertEquals(wrongPassword.bodyAsText(), unknownEmail.bodyAsText())
    }

    @Test
    fun `password is stored only as argon2id hash`() = apiTest { client ->
        val email = newEmail()
        client.postJson("/v1/parents", CredentialsRequest(email, PASSWORD))

        val stored = TestInfra.dataSource.connection.use { c ->
            c.queryOne("select password_hash from parents where email = ?", email) {
                it.getString(1)
            }
        }!!

        assertTrue(stored.startsWith("\$argon2id\$"))
        assertFalse(stored.contains(PASSWORD))
    }

    @Test
    fun `login attempts are rate limited`() = apiTest { client ->
        val email = newEmail()
        client.postJson("/v1/parents", CredentialsRequest(email, PASSWORD))

        val statuses = List(11) {
            client.postJson("/v1/sessions", CredentialsRequest(email, "wrong-password")).status
        }

        assertEquals(List(10) { HttpStatusCode.Unauthorized }, statuses.take(10))
        assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
    }

    @Test
    fun `expired session is rejected`() = apiTest { client ->
        val token = client.registerAndLogin()
        clock.now = clock.now.plusSeconds(31L * 24 * 60 * 60)

        val response = client.postJson("/v1/pairing-codes", mapOf<String, String>(), token)

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
