package uz.maxcare.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import uz.maxcare.backend.devices.PairResponse
import uz.maxcare.backend.devices.PairingCodeResponse
import uz.maxcare.backend.parents.CredentialsRequest
import uz.maxcare.backend.parents.SessionResponse

abstract class ApiTestBase {
    protected val clock = MutableClock(Instant.parse("2026-10-09T10:00:00Z"))

    @BeforeEach
    fun resetRateLimits() = TestInfra.flushRedis()

    protected fun apiTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { maxcareModule(TestInfra.services(clock)) }
            val client = createClient { install(ContentNegotiation) { json() } }
            block(client)
        }

    /** Синтетический адрес: каждый тест получает своего родителя. */
    protected fun newEmail() = "parent-${UUID.randomUUID()}@example.test"

    // reified: Ktor сериализует тело по статическому типу, с `Any` сериализатор не найдётся.
    protected suspend inline fun <reified T : Any> HttpClient.postJson(
        path: String,
        body: T,
        token: String? = null
    ) = post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
        token?.let { bearerAuth(it) }
    }

    protected suspend fun HttpClient.registerAndLogin(email: String = newEmail()): String {
        val credentials = CredentialsRequest(email, PASSWORD)
        assertEquals(HttpStatusCode.Created, postJson("/v1/parents", credentials).status)
        val session = postJson("/v1/sessions", credentials)
        assertEquals(HttpStatusCode.Created, session.status)
        return session.body<SessionResponse>().token
    }

    protected suspend fun HttpClient.newPairingCode(token: String): String {
        val response = postJson("/v1/pairing-codes", mapOf("childName" to "Test child"), token)
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body<PairingCodeResponse>().code
    }

    protected suspend fun HttpClient.pair(code: String) = postJson(
        "/v1/devices/pair",
        mapOf("code" to code, "deviceName" to "Test phone", "platform" to "android")
    )

    protected suspend fun HttpClient.pairedDevice(token: String): PairResponse {
        val response = pair(newPairingCode(token))
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    protected companion object {
        const val PASSWORD = "synthetic-password-1"
    }
}
