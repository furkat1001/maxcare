package uz.maxcare.backend

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
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
import uz.maxcare.backend.events.EventBatchRequest
import uz.maxcare.backend.events.EventDto
import uz.maxcare.backend.parents.CredentialsRequest
import uz.maxcare.backend.parents.DeleteAccountRequest
import uz.maxcare.backend.parents.IdTokenRequest
import uz.maxcare.backend.parents.SessionResponse

/** Удаление аккаунта родителя со всей семьёй из настроек приложения. */
class AccountDeleteApiTest : ApiTestBase() {

    private suspend fun HttpClient.deleteAccount(token: String?, request: DeleteAccountRequest) =
        postJson("/v1/account/delete", request, token)

    private fun googleToken(subject: String, email: String = newEmail()): String {
        val now = System.currentTimeMillis()
        val claims = JWTClaimsSet.Builder()
            .subject(subject)
            .issuer("https://accounts.google.com")
            .audience(TestInfra.GOOGLE_CLIENT_ID)
            .issueTime(Date(now - 60_000))
            .expirationTime(Date(now + 3_600_000))
            .claim("email", email)
            .claim("email_verified", true)
            .build()
        val key = TestInfra.googleKey
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
        jwt.sign(RSASSASigner(key))
        return jwt.serialize()
    }

    private suspend fun HttpClient.googleSession(idToken: String): String {
        val response = postJson("/v1/sessions/google", IdTokenRequest(idToken))
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body<SessionResponse>().token
    }

    @Test
    fun `the parent deletes the account with the children and all their data`() =
        apiTest { client ->
            val email = newEmail()
            val parent = client.registerAndLogin(email)
            val device = client.pairedDevice(parent)
            val event = EventDto(UUID.randomUUID().toString(), "location_off", clock.now.toString())
            client.postJson(
                "/v1/device/events",
                EventBatchRequest(listOf(event)),
                device.deviceToken
            )

            val response = client.deleteAccount(parent, DeleteAccountRequest(password = PASSWORD))

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals(HttpStatusCode.Unauthorized, client.getJson("/v1/account", parent).status)
            val phone = client.postJson(
                "/v1/device/events",
                EventBatchRequest(emptyList()),
                device.deviceToken
            )
            assertEquals(HttpStatusCode.Unauthorized, phone.status)
            val login = client.postJson("/v1/sessions", CredentialsRequest(email, PASSWORD))
            assertEquals(HttpStatusCode.Unauthorized, login.status)
            // Адрес освободился: можно зарегистрироваться заново, уже с пустой семьёй.
            val again = client.registerAndLogin(email)
            assertEquals(
                emptyList(),
                client.getJson("/v1/devices", again).body<List<DeviceSummaryDto>>()
            )
        }

    @Test
    fun `a wrong password keeps the account`() = apiTest { client ->
        val parent = client.registerAndLogin()

        val response = client.deleteAccount(
            parent,
            DeleteAccountRequest(password = "wrong-password")
        )

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(HttpStatusCode.OK, client.getJson("/v1/account", parent).status)
    }

    @Test
    fun `nothing to confirm with keeps the account`() = apiTest { client ->
        val parent = client.registerAndLogin()

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.deleteAccount(parent, DeleteAccountRequest()).status
        )
        assertEquals(HttpStatusCode.OK, client.getJson("/v1/account", parent).status)
    }

    @Test
    fun `a google-only parent confirms with the same google account`() = apiTest { client ->
        val subject = "google-${UUID.randomUUID()}"
        val parent = client.googleSession(googleToken(subject))

        val other = client.deleteAccount(
            parent,
            DeleteAccountRequest(idToken = googleToken("google-someone-else"))
        )
        assertEquals(HttpStatusCode.Unauthorized, other.status)

        val same = client.deleteAccount(
            parent,
            DeleteAccountRequest(idToken = googleToken(subject))
        )
        assertEquals(HttpStatusCode.NoContent, same.status)
        assertEquals(HttpStatusCode.Unauthorized, client.getJson("/v1/account", parent).status)
    }

    @Test
    fun `other families are not touched`() = apiTest { client ->
        val leaving = client.registerAndLogin()
        val staying = client.registerAndLogin()
        val stayingDevice = client.pairedDevice(staying)

        client.deleteAccount(leaving, DeleteAccountRequest(password = PASSWORD))

        val devices = client.getJson("/v1/devices", staying).body<List<DeviceSummaryDto>>()
        assertEquals(listOf(stayingDevice.deviceId), devices.map { it.id })
    }

    @Test
    fun `deleting needs a parent session`() = apiTest { client ->
        val response = client.deleteAccount(null, DeleteAccountRequest(password = PASSWORD))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
