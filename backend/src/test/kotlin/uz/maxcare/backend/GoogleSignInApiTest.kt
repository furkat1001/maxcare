package uz.maxcare.backend

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import uz.maxcare.backend.devices.DeviceSummaryDto
import uz.maxcare.backend.http.ApiError
import uz.maxcare.backend.parents.CredentialsRequest
import uz.maxcare.backend.parents.IdTokenRequest
import uz.maxcare.backend.parents.SessionResponse

class GoogleSignInApiTest : ApiTestBase() {

    /** Токен «как от Google». Срок — по настоящим часам: его проверяет библиотека, а не clock. */
    private fun googleToken(
        subject: String = "google-${UUID.randomUUID()}",
        email: String? = newEmail(),
        emailVerified: Boolean = true,
        audience: String = TestInfra.GOOGLE_CLIENT_ID,
        issuer: String = "https://accounts.google.com",
        expiresInSeconds: Long = 3600,
        key: RSAKey = TestInfra.googleKey
    ): String {
        val now = System.currentTimeMillis()
        val claims = JWTClaimsSet.Builder()
            .subject(subject)
            .issuer(issuer)
            .audience(audience)
            .issueTime(Date(now - 60_000))
            .expirationTime(Date(now + expiresInSeconds * 1000))
            .apply { email?.let { claim("email", it) } }
            .claim("email_verified", emailVerified)
            .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
        jwt.sign(RSASSASigner(key))
        return jwt.serialize()
    }

    private suspend fun HttpClient.googleLogin(idToken: String) =
        postJson("/v1/sessions/google", IdTokenRequest(idToken))

    private suspend fun HttpClient.googleSession(idToken: String): String {
        val response = googleLogin(idToken)
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body<SessionResponse>().token
    }

    private suspend fun HttpClient.deviceCount(parentToken: String) =
        getJson("/v1/devices", parentToken).body<List<DeviceSummaryDto>>().size

    @Test
    fun `first google sign in creates an account that works like any other`() = apiTest { client ->
        val token = client.googleSession(googleToken())

        assertEquals(0, client.deviceCount(token))
        client.pairedDevice(token)
        assertEquals(1, client.deviceCount(token))
    }

    @Test
    fun `the same google user returns to the same account`() = apiTest { client ->
        val subject = "google-${UUID.randomUUID()}"
        client.pairedDevice(client.googleSession(googleToken(subject = subject)))

        // Адрес в Google сменился — аккаунт всё равно тот же: связь по sub, не по e-mail.
        val again = client.googleSession(googleToken(subject = subject, email = newEmail()))

        assertEquals(1, client.deviceCount(again))
    }

    @Test
    fun `verified email links google to an existing password account`() = apiTest { client ->
        val email = newEmail()
        client.pairedDevice(client.registerAndLogin(email))

        val token = client.googleSession(googleToken(email = email.uppercase()))

        assertEquals(1, client.deviceCount(token))
        // Пароль после связки продолжает работать.
        val byPassword = client.postJson("/v1/sessions", CredentialsRequest(email, PASSWORD))
        assertEquals(HttpStatusCode.Created, byPassword.status)
    }

    @Test
    fun `unverified email does not take over an existing account`() = apiTest { client ->
        val email = newEmail()
        client.registerAndLogin(email)

        val response = client.googleLogin(googleToken(email = email, emailVerified = false))

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("email_taken", response.body<ApiError>().error)
    }

    @Test
    fun `token without email cannot create an account`() = apiTest { client ->
        val response = client.googleLogin(googleToken(email = null))

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `forged, foreign, expired and broken tokens are rejected`() = apiTest { client ->
        val forged = googleToken(key = RSAKeyGenerator(2048).keyID("test-google").generate())
        listOf(
            forged,
            googleToken(audience = "other-app.apps.googleusercontent.com"),
            googleToken(issuer = "https://evil.example"),
            googleToken(expiresInSeconds = -3600),
            "not-a-jwt"
        ).forEach { idToken ->
            val response = client.googleLogin(idToken)
            assertEquals(HttpStatusCode.Unauthorized, response.status, idToken.take(20))
        }
    }

    @Test
    fun `google-only account cannot sign in with a password`() = apiTest { client ->
        val email = newEmail()
        client.googleSession(googleToken(email = email))

        val response = client.postJson("/v1/sessions", CredentialsRequest(email, "any-password-1"))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `unknown provider is not found`() = apiTest { client ->
        assertEquals(
            HttpStatusCode.NotFound,
            client.postJson("/v1/sessions/facebook", IdTokenRequest(googleToken())).status
        )
        // Apple ещё не подключён: тот же ответ, пока для него нет проверки.
        assertEquals(
            HttpStatusCode.NotFound,
            client.postJson("/v1/sessions/apple", IdTokenRequest(googleToken())).status
        )
    }
}
