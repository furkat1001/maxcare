package uz.maxcare.backend

import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.devices.PairingCodeResponse
import uz.maxcare.backend.devices.RecoveryCodeResponse
import uz.maxcare.backend.http.ApiError
import uz.maxcare.backend.security.RecoveryCodes
import uz.maxcare.backend.security.Tokens

class DeviceApiTest : ApiTestBase() {

    @Test
    fun `pairing code needs a parent session`() = apiTest { client ->
        val response = client.postJson("/v1/pairing-codes", mapOf<String, String>())

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `pairing code is 8 digits and lives 10 minutes`() = apiTest { client ->
        val token = client.registerAndLogin()

        val response = client.postJson("/v1/pairing-codes", mapOf<String, String>(), token)
            .body<PairingCodeResponse>()

        assertTrue(response.code.matches(Regex("\\d{8}")))
        assertEquals(clock.now.plusSeconds(600).toString(), response.expiresAt)
    }

    @Test
    fun `device pairs with a code and gets its own token and recovery secret`() =
        apiTest { client ->
            val device = client.pairedDevice(client.registerAndLogin())

            assertNotNull(UUID.fromString(device.deviceId))
            assertTrue(device.deviceToken.length >= 40)
            assertEquals(32, Base64.getDecoder().decode(device.recoverySecret).size)
        }

    @Test
    fun `code works only once`() = apiTest { client ->
        val code = client.newPairingCode(client.registerAndLogin())
        client.pair(code)

        val second = client.pair(code)

        assertEquals(HttpStatusCode.BadRequest, second.status)
        assertEquals("invalid_code", second.body<ApiError>().error)
    }

    @Test
    fun `expired code is rejected`() = apiTest { client ->
        val code = client.newPairingCode(client.registerAndLogin())
        clock.now = clock.now.plusSeconds(601)

        assertEquals(HttpStatusCode.BadRequest, client.pair(code).status)
    }

    @Test
    fun `unknown platform is rejected`() = apiTest { client ->
        val code = client.newPairingCode(client.registerAndLogin())

        val response = client.postJson(
            "/v1/devices/pair",
            mapOf("code" to code, "deviceName" to "Test phone", "platform" to "symbian")
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `guessing codes is rate limited`() = apiTest { client ->
        val statuses = List(11) { client.pair("00000000").status }

        assertEquals(List(10) { HttpStatusCode.BadRequest }, statuses.take(10))
        assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
    }

    @Test
    fun `device token and recovery secret are not stored in plain form`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        val secret = Base64.getDecoder().decode(device.recoverySecret)

        val (tokenHash, secretEnc) = TestInfra.dataSource.connection.use { c ->
            c.queryOne(
                "select token_hash, recovery_secret_enc from devices where id = ?",
                UUID.fromString(device.deviceId)
            ) { it.getBytes(1) to it.getBytes(2) }
        }!!

        assertTrue(tokenHash.contentEquals(Tokens.hash(device.deviceToken)))
        assertFalse(secretEnc.contentEquals(secret))
        assertFalse(secretEnc.toList().windowed(secret.size).any { it == secret.toList() })
    }

    @Test
    fun `parent gets a recovery code the phone can verify offline`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        val response = client.postJson(
            "/v1/devices/${device.deviceId}/recovery-code",
            mapOf<String, String>(),
            token
        )
        assertEquals(HttpStatusCode.OK, response.status)
        val recovery = response.body<RecoveryCodeResponse>()

        // То же вычисление, что сделает телефон по секрету из ответа на привязку.
        val secret = Base64.getDecoder().decode(device.recoverySecret)
        assertEquals(RecoveryCodes.code(secret, RecoveryCodes.counterAt(clock.now)), recovery.code)
    }

    @Test
    fun `parent cannot get a recovery code for another family's device`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        val otherParent = client.registerAndLogin()

        val response = client.postJson(
            "/v1/devices/${device.deviceId}/recovery-code",
            mapOf<String, String>(),
            otherParent
        )

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `malformed device id is not found`() = apiTest { client ->
        val response = client.postJson(
            "/v1/devices/not-a-uuid/recovery-code",
            mapOf<String, String>(),
            client.registerAndLogin()
        )

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
