package uz.maxcare.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import uz.maxcare.backend.devices.DeviceSummaryDto
import uz.maxcare.backend.devices.PairResponse
import uz.maxcare.backend.devices.PairingCodeResponse
import uz.maxcare.backend.devices.RecoveryCodeResponse
import uz.maxcare.backend.events.DeviceActivityResponse
import uz.maxcare.backend.events.EventBatchRequest
import uz.maxcare.backend.events.EventDto
import uz.maxcare.backend.security.RecoveryCodes

/** Новый телефон для существующего ребёнка и удаление ребёнка родителем. */
class DeviceReconnectApiTest : ApiTestBase() {

    private suspend fun HttpClient.reconnectCode(deviceId: String, parentToken: String) =
        postJson("/v1/devices/$deviceId/pairing-code", mapOf<String, String>(), parentToken)

    private suspend fun HttpClient.reconnect(deviceId: String, parentToken: String): PairResponse {
        val code = reconnectCode(deviceId, parentToken)
        assertEquals(HttpStatusCode.Created, code.status)
        val paired = pair(code.body<PairingCodeResponse>().code)
        assertEquals(HttpStatusCode.Created, paired.status)
        return paired.body()
    }

    private suspend fun HttpClient.devices(parentToken: String) =
        getJson("/v1/devices", parentToken).body<List<DeviceSummaryDto>>()

    private suspend fun HttpClient.eventTypes(deviceId: String, parentToken: String) =
        getJson("/v1/devices/$deviceId/events", parentToken)
            .body<DeviceActivityResponse>().events.map { it.type }

    private suspend fun HttpClient.sendEvent(deviceToken: String, type: String) = postJson(
        "/v1/device/events",
        EventBatchRequest(
            listOf(EventDto(UUID.randomUUID().toString(), type, clock.now.toString()))
        ),
        deviceToken
    )

    @Test
    fun `a new phone takes the place of the child and keeps the history`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val old = client.pairedDevice(parent)
        client.sendEvent(old.deviceToken, "location_off")

        val new = client.reconnect(old.deviceId, parent)

        assertEquals(old.deviceId, new.deviceId)
        assertEquals(listOf(old.deviceId), client.devices(parent).map { it.id })
        assertEquals(
            setOf("location_off", "phone_reconnected"),
            client.eventTypes(old.deviceId, parent).toSet()
        )
    }

    @Test
    fun `the old phone loses access and the new one gets its own`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val old = client.pairedDevice(parent)

        val new = client.reconnect(old.deviceId, parent)

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.sendEvent(old.deviceToken, "device_booted").status
        )
        assertEquals(HttpStatusCode.OK, client.sendEvent(new.deviceToken, "device_booted").status)
        assertNotEquals(old.recoverySecret, new.recoverySecret)
    }

    @Test
    fun `recovery codes follow the new phone`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val old = client.pairedDevice(parent)
        val new = client.reconnect(old.deviceId, parent)

        val recovery = client.postJson(
            "/v1/devices/${old.deviceId}/recovery-code",
            mapOf<String, String>(),
            parent
        ).body<RecoveryCodeResponse>()

        val secret = Base64.getDecoder().decode(new.recoverySecret)
        assertEquals(RecoveryCodes.code(secret, RecoveryCodes.counterAt(clock.now)), recovery.code)
    }

    @Test
    fun `reconnecting does not need a free place in the child limit`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val devices = List(3) { client.pairedDevice(parent) }

        client.reconnect(devices.first().deviceId, parent)

        assertEquals(3, client.devices(parent).size)
    }

    @Test
    fun `reconnect code is only for the family's own child`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        val otherParent = client.registerAndLogin()

        assertEquals(
            HttpStatusCode.NotFound,
            client.reconnectCode(device.deviceId, otherParent).status
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.reconnectCode("not-a-uuid", otherParent).status
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.postJson(
                "/v1/devices/${device.deviceId}/pairing-code",
                mapOf<String, String>()
            ).status
        )
    }

    @Test
    fun `a reconnect code works only once`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)
        val code = client.reconnectCode(device.deviceId, parent).body<PairingCodeResponse>().code

        assertEquals(HttpStatusCode.Created, client.pair(code).status)
        assertEquals(HttpStatusCode.BadRequest, client.pair(code).status)
    }

    @Test
    fun `a phone cannot fake the reconnect event`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)

        client.sendEvent(device.deviceToken, "phone_reconnected")

        assertEquals(emptyList(), client.eventTypes(device.deviceId, parent))
    }

    @Test
    fun `parent deletes a child with its history and frees a place`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val devices = List(3) { client.pairedDevice(parent) }
        val gone = devices.first()
        client.sendEvent(gone.deviceToken, "location_off")

        val response = client.delete("/v1/devices/${gone.deviceId}") { bearerAuth(parent) }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(
            devices.drop(1).map {
                it.deviceId
            }.toSet(),
            client.devices(parent).map { it.id }.toSet()
        )
        val events = client.get("/v1/devices/${gone.deviceId}/events") { bearerAuth(parent) }
        assertEquals(HttpStatusCode.NotFound, events.status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.sendEvent(gone.deviceToken, "device_booted").status
        )
        val code = client.postJson("/v1/pairing-codes", mapOf("childName" to "Next"), parent)
        assertEquals(HttpStatusCode.Created, code.status)
    }

    @Test
    fun `parent cannot delete another family's child`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        val otherParent = client.registerAndLogin()

        val response = client.delete("/v1/devices/${device.deviceId}") { bearerAuth(otherParent) }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.delete("/v1/devices/${device.deviceId}").status
        )
    }
}
