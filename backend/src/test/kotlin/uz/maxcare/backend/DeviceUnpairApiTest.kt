package uz.maxcare.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import uz.maxcare.backend.devices.DeviceSummaryDto
import uz.maxcare.backend.events.EventBatchRequest
import uz.maxcare.backend.events.EventDto

class DeviceUnpairApiTest : ApiTestBase() {

    private suspend fun HttpClient.unpair(deviceToken: String?) =
        delete("/v1/device") { deviceToken?.let { bearerAuth(it) } }

    private suspend fun HttpClient.devices(parentToken: String) =
        getJson("/v1/devices", parentToken).body<List<DeviceSummaryDto>>()

    @Test
    fun `unpair needs a device token`() = apiTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.unpair(null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.unpair("unknown-token").status)
    }

    @Test
    fun `unpaired child disappears for the parent with its history`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)
        val event = EventDto(UUID.randomUUID().toString(), "location_off", clock.now.toString())
        client.postJson("/v1/device/events", EventBatchRequest(listOf(event)), device.deviceToken)

        assertEquals(HttpStatusCode.NoContent, client.unpair(device.deviceToken).status)

        assertEquals(emptyList(), client.devices(parent))
        val activity = client.get("/v1/devices/${device.deviceId}/events") { bearerAuth(parent) }
        assertEquals(HttpStatusCode.NotFound, activity.status)
    }

    @Test
    fun `old token stops working after unpair`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        client.unpair(device.deviceToken)

        assertEquals(HttpStatusCode.Unauthorized, client.unpair(device.deviceToken).status)
        val limits = client.getJson("/v1/device/app-limits", device.deviceToken)
        assertEquals(HttpStatusCode.Unauthorized, limits.status)
    }

    @Test
    fun `unpair frees a place in the child limit`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val devices = List(3) { client.pairedDevice(parent) }

        client.unpair(devices.first().deviceToken)

        val code = client.postJson("/v1/pairing-codes", mapOf("childName" to "Next"), parent)
        assertEquals(HttpStatusCode.Created, code.status)
    }

    @Test
    fun `other phones of the family stay paired`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val leaving = client.pairedDevice(parent)
        val staying = client.pairedDevice(parent)

        client.unpair(leaving.deviceToken)

        assertEquals(listOf(staying.deviceId), client.devices(parent).map { it.id })
    }
}
