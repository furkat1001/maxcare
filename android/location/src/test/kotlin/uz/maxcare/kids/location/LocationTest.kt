package uz.maxcare.kids.location

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.events.SyncResult
import uz.maxcare.kids.core.location.LocationFix
import uz.maxcare.kids.core.location.LocationProvider
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.network.OutgoingEvent
import uz.maxcare.kids.core.network.OutgoingLocation
import uz.maxcare.kids.core.network.PairedDevice
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException

class SecureLocationQueueTest {
    private val store = InMemoryStore()
    private val queue = SecureLocationQueue(store)

    private fun point(n: Int) =
        QueuedLocation("id-$n", 41.0 + n, 69.0, 10.0, "2026-10-09T10:00:00Z")

    @Test
    fun `points come out in order and are removed by id`() = runTest {
        repeat(3) { queue.add(point(it)) }

        assertEquals(listOf("id-0", "id-1"), queue.peek(2).map { it.id })
        queue.remove(setOf("id-0", "id-1"))
        assertEquals(listOf("id-2"), queue.peek(10).map { it.id })
    }

    @Test
    fun `queue survives a restart`() = runTest {
        queue.add(point(1))

        assertEquals(listOf("id-1"), SecureLocationQueue(store).peek(10).map { it.id })
    }

    @Test
    fun `overflow drops the oldest points`() = runTest {
        repeat(SecureLocationQueue.MAX_LOCATIONS + 2) { queue.add(point(it)) }

        val kept = queue.peek(SecureLocationQueue.MAX_LOCATIONS + 10)
        assertEquals(SecureLocationQueue.MAX_LOCATIONS, kept.size)
        assertEquals("id-2", kept.first().id)
    }

    @Test
    fun `damaged queue reads as empty and is replaced`() = runTest {
        store.values["location_queue"] = "not json".encodeToByteArray()

        assertTrue(queue.peek(10).isEmpty())
        queue.add(point(1))
        assertEquals(listOf("id-1"), queue.peek(10).map { it.id })
    }
}

class LocationUploaderTest {
    private val store = InMemoryStore().apply {
        values[SecureKeys.DEVICE_TOKEN] = "device-token".encodeToByteArray()
    }
    private val queue = SecureLocationQueue(store)
    private val api = FakeApi()
    private val uploader = LocationUploader(queue, api, store)

    private suspend fun fill(count: Int) = repeat(count) {
        queue.add(QueuedLocation("id-$it", 41.0, 69.0, 10.0, "2026-10-09T10:00:00Z"))
    }

    @Test
    fun `empty queue sends nothing and reports delivered`() = runTest {
        assertEquals(SyncResult.DELIVERED, uploader.sync())

        assertTrue(api.batches.isEmpty())
    }

    @Test
    fun `delivered points leave the queue`() = runTest {
        fill(3)

        assertEquals(SyncResult.DELIVERED, uploader.sync())

        assertEquals(listOf("id-0", "id-1", "id-2"), api.batches.single().map { it.id })
        assertEquals("device-token", api.lastToken)
        assertTrue(queue.peek(10).isEmpty())
    }

    @Test
    fun `large queue goes in batches of 100`() = runTest {
        fill(250)

        assertEquals(SyncResult.DELIVERED, uploader.sync())

        assertEquals(listOf(100, 100, 50), api.batches.map { it.size })
        assertTrue(queue.peek(10).isEmpty())
    }

    @Test
    fun `failed delivery keeps points for the next attempt`() = runTest {
        fill(2)
        api.result = ApiResult.NoConnection

        assertEquals(SyncResult.RETRY_LATER, uploader.sync())

        assertEquals(2, queue.peek(10).size)
    }

    @Test
    fun `revoked token means not paired, so no retries`() = runTest {
        fill(2)
        api.result = ApiResult.HttpError(401, "unauthorized")

        assertEquals(SyncResult.NOT_PAIRED, uploader.sync())
        assertEquals(2, queue.peek(10).size)
    }

    @Test
    fun `unpaired phone sends nothing`() = runTest {
        store.values.remove(SecureKeys.DEVICE_TOKEN)

        assertEquals(SyncResult.NOT_PAIRED, uploader.sync())
        assertTrue(api.locationBatches.isEmpty())
    }
}

class PeriodicLocationCollectorTest {
    private val store = InMemoryStore()
    private val queue = SecureLocationQueue(store)
    private val provider = FakeProvider()
    private val clock = Clock.fixed(Instant.parse("2026-10-09T10:15:30Z"), ZoneOffset.UTC)
    private val collector = PeriodicLocationCollector(provider, queue, clock)

    @Test
    fun `a fix is queued with coordinates, accuracy and the clock time`() = runTest {
        provider.fix = LocationFix(41.3, 69.25, 8.0)

        assertTrue(collector.collect())

        val queued = queue.peek(10).single()
        assertEquals(41.3, queued.latitude, 0.0)
        assertEquals(69.25, queued.longitude, 0.0)
        assertEquals(8.0, queued.accuracyMeters!!, 0.0)
        assertEquals("2026-10-09T10:15:30Z", queued.recordedAt)
    }

    @Test
    fun `no fix means nothing is queued`() = runTest {
        provider.fix = null

        assertFalse(collector.collect())
        assertTrue(queue.peek(10).isEmpty())
    }

    @Test
    fun `missing accuracy is allowed`() = runTest {
        provider.fix = LocationFix(41.0, 69.0, null)

        collector.collect()

        assertNull(queue.peek(10).single().accuracyMeters)
    }

    @Test
    fun `broken storage does not crash`() = runTest {
        store.failWrites = true
        provider.fix = LocationFix(41.0, 69.0, 5.0)

        assertFalse(collector.collect())
    }
}

private class FakeProvider : LocationProvider {
    var fix: LocationFix? = null
    override suspend fun current(): LocationFix? = fix
}

private class InMemoryStore : SecureStore {
    val values = mutableMapOf<String, ByteArray>()
    var failWrites = false

    override suspend fun read(key: String) = values[key]?.copyOf()

    override suspend fun write(key: String, value: ByteArray) {
        if (failWrites) throw SecureStoreException("broken", IOException())
        values[key] = value.copyOf()
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}

private class FakeApi : MaxCareApi {
    var result: ApiResult<Unit> = ApiResult.Ok(Unit)
    val locationBatches = mutableListOf<List<OutgoingLocation>>()
    var lastToken: String? = null

    val batches: List<List<OutgoingLocation>> get() = locationBatches

    override suspend fun pairDevice(code: String, deviceName: String): ApiResult<PairedDevice> =
        error("Not used")

    override suspend fun uploadEvents(
        deviceToken: String,
        events: List<OutgoingEvent>
    ): ApiResult<Unit> = error("Not used")

    override suspend fun uploadLocations(
        deviceToken: String,
        locations: List<OutgoingLocation>
    ): ApiResult<Unit> {
        lastToken = deviceToken
        locationBatches += locations
        return result
    }

    override suspend fun uploadAppUsage(
        deviceToken: String,
        days: List<uz.maxcare.kids.core.network.OutgoingDayUsage>
    ): ApiResult<Unit> = error("Not used")

    override suspend fun fetchFilterConfig(deviceToken: String) = error("Not used")

    override suspend fun fetchAppLimits(deviceToken: String) = error("Not used")
}
