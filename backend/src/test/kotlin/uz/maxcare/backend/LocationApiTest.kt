package uz.maxcare.backend

import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import uz.maxcare.backend.location.DeviceLocationResponse
import uz.maxcare.backend.location.LocationBatchRequest
import uz.maxcare.backend.location.LocationReportRequest

class LocationApiTest : ApiTestBase() {

    // Ташкент — синтетические координаты, не настоящий ребёнок.
    private val tashkentLat = 41.311081
    private val tashkentLon = 69.240562

    private suspend fun io.ktor.client.HttpClient.reportLocation(
        deviceToken: String,
        latitude: Double = tashkentLat,
        longitude: Double = tashkentLon,
        accuracyMeters: Double? = 12.5,
        recordedAt: String = clock.now.toString()
    ) = postJson(
        "/v1/device/location",
        LocationReportRequest(
            id = UUID.randomUUID().toString(),
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            recordedAt = recordedAt
        ),
        deviceToken
    )

    @Test
    fun `reporting a location needs a device session`() = apiTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.reportLocation("not-a-token").status)
    }

    @Test
    fun `reading a location needs a parent session`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.getJson("/v1/devices/${device.deviceId}/location").status
        )
    }

    @Test
    fun `parent reads the latest reported location`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        assertEquals(HttpStatusCode.Accepted, client.reportLocation(device.deviceToken).status)
        clock.now = clock.now.plusSeconds(60)
        val later = client.reportLocation(
            device.deviceToken,
            latitude = 41.5,
            longitude = 69.5,
            accuracyMeters = 8.0
        )
        assertEquals(HttpStatusCode.Accepted, later.status)

        val response = client.getJson("/v1/devices/${device.deviceId}/location", token)
        assertEquals(HttpStatusCode.OK, response.status)
        val location = response.body<DeviceLocationResponse>()
        assertEquals(41.5, location.latitude)
        assertEquals(69.5, location.longitude)
        assertEquals(8.0, location.accuracyMeters)
    }

    @Test
    fun `location without accuracy is accepted`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        assertEquals(
            HttpStatusCode.Accepted,
            client.reportLocation(device.deviceToken, accuracyMeters = null).status
        )
        val location = client.getJson("/v1/devices/${device.deviceId}/location", token)
            .body<DeviceLocationResponse>()
        assertEquals(null, location.accuracyMeters)
    }

    @Test
    fun `a device with no location yet returns no content`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        val response = client.getJson("/v1/devices/${device.deviceId}/location", token)
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `location is scoped to the parent's family`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        client.reportLocation(device.deviceToken)
        val otherParent = client.registerAndLogin()

        val response = client.getJson("/v1/devices/${device.deviceId}/location", otherParent)
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `out of range coordinates are rejected`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())

        val response = client.reportLocation(device.deviceToken, latitude = 100.0)
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `malformed device id is not found`() = apiTest { client ->
        val response = client.getJson("/v1/devices/not-a-uuid/location", client.registerAndLogin())
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `history returns the day's track in chronological order`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        client.reportLocation(device.deviceToken, latitude = 41.1)
        clock.now = clock.now.plusSeconds(300)
        client.reportLocation(device.deviceToken, latitude = 41.2)
        clock.now = clock.now.plusSeconds(300)
        client.reportLocation(device.deviceToken, latitude = 41.3)

        val track = client.getJson("/v1/devices/${device.deviceId}/locations", token)
            .body<List<DeviceLocationResponse>>()

        assertEquals(listOf(41.1, 41.2, 41.3), track.map { it.latitude })
    }

    @Test
    fun `history can start from a given moment`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        client.reportLocation(device.deviceToken, latitude = 41.1)
        clock.now = clock.now.plusSeconds(600)
        val cutoff = clock.now
        client.reportLocation(device.deviceToken, latitude = 41.2)

        val track = client.getJson(
            "/v1/devices/${device.deviceId}/locations?since=$cutoff",
            token
        ).body<List<DeviceLocationResponse>>()

        assertEquals(listOf(41.2), track.map { it.latitude })
    }

    @Test
    fun `history is scoped to the parent's family`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        client.reportLocation(device.deviceToken)
        val otherParent = client.registerAndLogin()

        val response = client.getJson("/v1/devices/${device.deviceId}/locations", otherParent)
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private fun point(latitude: Double, recordedAt: String) = LocationReportRequest(
        id = UUID.randomUUID().toString(),
        latitude = latitude,
        longitude = tashkentLon,
        accuracyMeters = 10.0,
        recordedAt = recordedAt
    )

    @Test
    fun `a batch of locations is stored and read back as a track`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        val response = client.postJson(
            "/v1/device/locations",
            LocationBatchRequest(
                listOf(
                    point(41.1, clock.now.toString()),
                    point(41.2, clock.now.plusSeconds(60).toString())
                )
            ),
            device.deviceToken
        )
        assertEquals(HttpStatusCode.OK, response.status)

        val track = client.getJson("/v1/devices/${device.deviceId}/locations", token)
            .body<List<DeviceLocationResponse>>()
        assertEquals(listOf(41.1, 41.2), track.map { it.latitude })
    }

    @Test
    fun `a batch with one bad point is rejected whole`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())

        val response = client.postJson(
            "/v1/device/locations",
            LocationBatchRequest(
                listOf(point(41.1, clock.now.toString()), point(999.0, clock.now.toString()))
            ),
            device.deviceToken
        )
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `history with a malformed since is rejected`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        val response = client.getJson(
            "/v1/devices/${device.deviceId}/locations?since=not-a-date",
            token
        )
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
