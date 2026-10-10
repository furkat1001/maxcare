package uz.maxcare.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uz.maxcare.backend.events.DeviceActivityResponse
import uz.maxcare.backend.events.EventBatchRequest
import uz.maxcare.backend.events.EventBatchResponse
import uz.maxcare.backend.events.EventDto

class EventApiTest : ApiTestBase() {

    private fun event(type: String, at: String = "2026-10-09T09:58:00Z", id: String = newId()) =
        EventDto(id, type, at)

    private fun newId() = UUID.randomUUID().toString()

    private suspend fun HttpClient.send(deviceToken: String, vararg events: EventDto) =
        postJson("/v1/device/events", EventBatchRequest(events.toList()), deviceToken)

    private suspend fun HttpClient.activity(deviceId: String, parentToken: String) =
        get("/v1/devices/$deviceId/events") { bearerAuth(parentToken) }

    @Test
    fun `new device has not been seen yet`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)

        val activity = client.activity(device.deviceId, parent).body<DeviceActivityResponse>()

        assertNull(activity.lastSeenAt)
        assertEquals(emptyList(), activity.events)
    }

    @Test
    fun `empty batch is a heartbeat`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)

        val response = client.send(device.deviceToken)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, response.body<EventBatchResponse>().accepted)
        val activity = client.activity(device.deviceId, parent).body<DeviceActivityResponse>()
        assertEquals(clock.now.toString(), activity.lastSeenAt)
    }

    @Test
    fun `events are stored and shown to the parent newest first`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)

        client.send(device.deviceToken, event("device_booted", "2026-10-09T07:30:00Z"))
        clock.now = clock.now.plusSeconds(60)
        client.send(device.deviceToken, event("admin_disabled", "2026-10-09T10:00:30Z"))

        val events = client.activity(device.deviceId, parent).body<DeviceActivityResponse>().events
        assertEquals(listOf("admin_disabled", "device_booted"), events.map { it.type })
        assertEquals("2026-10-09T10:00:30Z", events.first().occurredAt)
        assertEquals(clock.now.toString(), events.first().receivedAt)
    }

    @Test
    fun `resent events are not duplicated`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)
        val booted = event("device_booted")

        val first = client.send(device.deviceToken, booted).body<EventBatchResponse>()
        val second = client.send(device.deviceToken, booted).body<EventBatchResponse>()

        assertEquals(1, first.accepted)
        assertEquals(0, second.accepted)
        assertEquals(
            1,
            client.activity(device.deviceId, parent).body<DeviceActivityResponse>().events.size
        )
    }

    @Test
    fun `unknown event type is skipped, not rejected`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)

        val response = client.send(
            device.deviceToken,
            event("from_a_newer_app"),
            event("device_booted")
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, response.body<EventBatchResponse>().accepted)
    }

    @Test
    fun `time from the future is kept as reported`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)

        client.send(device.deviceToken, event("device_booted", "2030-01-01T00:00:00Z"))

        val stored = client.activity(
            device.deviceId,
            parent
        ).body<DeviceActivityResponse>().events.single()
        assertEquals("2030-01-01T00:00:00Z", stored.occurredAt)
        assertEquals(clock.now.toString(), stored.receivedAt)
    }

    @Test
    fun `malformed batch is rejected`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())

        assertEquals(
            HttpStatusCode.BadRequest,
            client.send(device.deviceToken, event("device_booted", id = "not-a-uuid")).status
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.send(device.deviceToken, event("device_booted", at = "yesterday")).status
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.send(device.deviceToken, *Array(101) { event("device_booted") }).status
        )
    }

    @Test
    fun `events need a device token, not a parent token`() = apiTest { client ->
        val parent = client.registerAndLogin()

        assertEquals(HttpStatusCode.Unauthorized, client.send("not-a-token").status)
        assertEquals(HttpStatusCode.Unauthorized, client.send(parent).status)
    }

    @Test
    fun `parent cannot see another family's device`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        val otherParent = client.registerAndLogin()

        assertEquals(HttpStatusCode.NotFound, client.activity(device.deviceId, otherParent).status)
    }

    @Test
    fun `device token does not open the parent view`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.activity(device.deviceId, device.deviceToken).status
        )
    }

    @Test
    fun `event uploads are rate limited per device`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())

        val statuses = List(121) { client.send(device.deviceToken).status }

        assertEquals(HttpStatusCode.OK, statuses[119])
        assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
    }

    @Test
    fun `blocked site address reaches the parent`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)

        client.send(
            device.deviceToken,
            EventDto(
                newId(),
                "site_blocked_filtered",
                "2026-10-09T10:00:00Z",
                "m.example-adult.test"
            )
        )

        val stored = client.activity(
            device.deviceId,
            parent
        ).body<DeviceActivityResponse>().events.single()
        assertEquals("site_blocked_filtered", stored.type)
        assertEquals("m.example-adult.test", stored.detail)
    }

    @Test
    fun `detail that is not a plain domain is dropped, the event is kept`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)

        val response = client.send(
            device.deviceToken,
            EventDto(
                newId(),
                "site_blocked_filtered",
                "2026-10-09T10:00:00Z",
                "Not a domain! <script>"
            ),
            EventDto(
                newId(),
                "site_blocked_gambling",
                "2026-10-09T10:00:00Z",
                "a".repeat(250) + ".test"
            )
        )

        assertEquals(2, response.body<EventBatchResponse>().accepted)
        val events = client.activity(device.deviceId, parent).body<DeviceActivityResponse>().events
        assertEquals(listOf(null, null), events.map { it.detail })
    }
}
