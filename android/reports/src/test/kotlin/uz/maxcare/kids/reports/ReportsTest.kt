package uz.maxcare.kids.reports

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.events.EventSync
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.events.SyncResult
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.network.OutgoingEvent
import uz.maxcare.kids.core.network.PairedDevice
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException

class SecureEventQueueTest {
    private val store = InMemoryStore()
    private val queue = SecureEventQueue(store)

    private fun event(n: Int) = QueuedEvent("id-$n", "device_booted", "2026-10-09T10:00:00Z")

    @Test
    fun `events come out in order and are removed by id`() = runTest {
        repeat(3) { queue.add(event(it)) }

        assertEquals(listOf("id-0", "id-1"), queue.peek(2).map { it.id })
        queue.remove(setOf("id-0", "id-1"))
        assertEquals(listOf("id-2"), queue.peek(10).map { it.id })
    }

    @Test
    fun `queue survives a restart`() = runTest {
        queue.add(event(1))

        assertEquals(listOf("id-1"), SecureEventQueue(store).peek(10).map { it.id })
    }

    @Test
    fun `overflow drops the oldest events`() = runTest {
        repeat(SecureEventQueue.MAX_EVENTS + 2) { queue.add(event(it)) }

        val kept = queue.peek(SecureEventQueue.MAX_EVENTS + 10)
        assertEquals(SecureEventQueue.MAX_EVENTS, kept.size)
        assertEquals("id-2", kept.first().id)
    }

    @Test
    fun `damaged queue reads as empty and is replaced`() = runTest {
        store.values["event_queue"] = "not json".encodeToByteArray()

        assertTrue(queue.peek(10).isEmpty())
        queue.add(event(1))
        assertEquals(listOf("id-1"), queue.peek(10).map { it.id })
    }
}

class EventUploaderTest {
    private val store = InMemoryStore().apply {
        values[SecureKeys.DEVICE_TOKEN] = "device-token".encodeToByteArray()
    }
    private val queue = SecureEventQueue(store)
    private val api = FakeApi()
    private val uploader = EventUploader(queue, api, store)

    private suspend fun fill(count: Int) =
        repeat(count) { queue.add(QueuedEvent("id-$it", "device_booted", "2026-10-09T10:00:00Z")) }

    @Test
    fun `empty queue still sends a heartbeat`() = runTest {
        assertEquals(SyncResult.DELIVERED, uploader.sync())

        assertEquals(listOf(0), api.batches.map { it.size })
        assertEquals("device-token", api.lastToken)
    }

    @Test
    fun `delivered events leave the queue`() = runTest {
        fill(3)

        assertEquals(SyncResult.DELIVERED, uploader.sync())

        assertEquals(listOf("id-0", "id-1", "id-2"), api.batches.single().map { it.id })
        assertTrue(queue.peek(10).isEmpty())
    }

    @Test
    fun `address is sent to the server`() = runTest {
        queue.add(
            QueuedEvent("id-1", "site_blocked_filtered", "2026-10-09T10:00:00Z", "a.example.test")
        )

        uploader.sync()

        assertEquals("a.example.test", api.batches.single().single().detail)
    }

    @Test
    fun `large queue goes in batches of 100`() = runTest {
        fill(250)

        assertEquals(SyncResult.DELIVERED, uploader.sync())

        assertEquals(listOf(100, 100, 50), api.batches.map { it.size })
        assertTrue(queue.peek(10).isEmpty())
    }

    @Test
    fun `failed delivery keeps events for the next attempt`() = runTest {
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

        assertEquals(1, api.batches.size)
        assertEquals(2, queue.peek(10).size)
    }

    @Test
    fun `unpaired phone sends nothing`() = runTest {
        store.values.remove(SecureKeys.DEVICE_TOKEN)

        assertEquals(SyncResult.NOT_PAIRED, uploader.sync())

        assertTrue(api.batches.isEmpty())
    }

    @Test
    fun `server error asks for a retry`() = runTest {
        api.result = ApiResult.HttpError(500, "internal_error")

        assertEquals(SyncResult.RETRY_LATER, uploader.sync())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QueueingEventRecorderTest {
    private val store = InMemoryStore()
    private val queue = SecureEventQueue(store)
    private val sync = CountingSync()
    private var scheduled = 0
    private val clock = Clock.fixed(Instant.parse("2026-10-09T10:15:30Z"), ZoneOffset.UTC)

    private fun TestScope.recorder(): QueueingEventRecorder = QueueingEventRecorder(
        queue,
        sync,
        { scheduled++ },
        clock,
        CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    )

    @Test
    fun `event is queued with wire name, time and a unique id, then sent`() = runTest {
        val recorder = recorder()

        recorder.record(EventType.ADMIN_DISABLED)
        recorder.record(EventType.ADMIN_DISABLED)

        val queued = queue.peek(10)
        assertEquals(listOf("admin_disabled", "admin_disabled"), queued.map { it.type })
        assertEquals("2026-10-09T10:15:30Z", queued.first().occurredAt)
        assertEquals(2, queued.map { it.id }.toSet().size)
        assertEquals(2, sync.calls)
    }

    @Test
    fun `blocked site address travels with the event`() = runTest {
        recorder().record(EventType.SITE_BLOCKED_GAMBLING, "1xbet.com")

        assertEquals("1xbet.com", queue.peek(10).single().detail)
    }

    @Test
    fun `broken storage does not crash the caller`() = runTest {
        store.failWrites = true

        recorder().record(EventType.DEVICE_BOOTED)

        assertEquals(0, sync.calls)
    }

    @Test
    fun `failed sending is handed over to WorkManager`() = runTest {
        sync.result = SyncResult.RETRY_LATER

        recorder().record(EventType.ADMIN_DISABLED)

        assertEquals(1, scheduled)
    }

    @Test
    fun `delivered or unpaired needs no retry`() = runTest {
        val recorder = recorder()
        recorder.record(EventType.DEVICE_BOOTED)
        sync.result = SyncResult.NOT_PAIRED
        recorder.record(EventType.DEVICE_BOOTED)

        assertEquals(0, scheduled)
    }
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
    val batches = mutableListOf<List<OutgoingEvent>>()
    var lastToken: String? = null

    override suspend fun pairDevice(code: String, deviceName: String): ApiResult<PairedDevice> =
        error("Not used")

    override suspend fun uploadEvents(
        deviceToken: String,
        events: List<OutgoingEvent>
    ): ApiResult<Unit> {
        lastToken = deviceToken
        batches += events
        return result
    }

    override suspend fun uploadLocations(
        deviceToken: String,
        locations: List<uz.maxcare.kids.core.network.OutgoingLocation>
    ): ApiResult<Unit> = error("Not used")

    override suspend fun uploadAppUsage(
        deviceToken: String,
        days: List<uz.maxcare.kids.core.network.OutgoingDayUsage>
    ): ApiResult<Unit> = error("Not used")

    override suspend fun fetchFilterConfig(deviceToken: String) =
        error("Not used")
}

private class CountingSync : EventSync {
    var calls = 0
    var result = SyncResult.DELIVERED

    override suspend fun sync(): SyncResult {
        calls++
        return result
    }
}
