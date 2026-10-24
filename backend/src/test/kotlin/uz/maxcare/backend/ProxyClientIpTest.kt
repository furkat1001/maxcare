package uz.maxcare.backend

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach

/** Регистрация ограничена 5 разами в час с одного адреса — по ней видно, какой IP берёт сервер. */
class ProxyClientIpTest {
    private val clock = MutableClock(java.time.Instant.parse("2026-10-09T10:00:00Z"))

    @BeforeEach
    fun resetRateLimits() = TestInfra.flushRedis()

    private fun registrations(
        behindProxy: Boolean,
        forwardedFor: (Int) -> String
    ): List<HttpStatusCode> {
        val statuses = mutableListOf<HttpStatusCode>()
        testApplication {
            application { maxcareModule(TestInfra.services(clock), behindProxy) }
            repeat(6) { i ->
                statuses += client.post("/v1/parents") {
                    header("X-Forwarded-For", forwardedFor(i))
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"email":"p-${UUID.randomUUID()}@example.test","password":"synthetic-password-1"}"""
                    )
                }.status
            }
        }
        return statuses
    }

    @Test
    fun `behind the proxy each client has its own limit`() {
        val statuses = registrations(behindProxy = true) { i -> "203.0.113.$i" }

        assertEquals(List(6) { HttpStatusCode.Created }, statuses)
    }

    @Test
    fun `behind the proxy only the address the proxy added counts`() {
        // Клиент подставляет разные адреса слева, прокси дописывает настоящий справа.
        val statuses = registrations(behindProxy = true) { i -> "198.51.100.$i, 203.0.113.7" }

        assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
    }

    @Test
    fun `without the proxy the header is ignored`() {
        val statuses = registrations(behindProxy = false) { i -> "203.0.113.$i" }

        assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
    }
}
