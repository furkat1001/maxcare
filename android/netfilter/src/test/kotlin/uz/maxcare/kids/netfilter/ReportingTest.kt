package uz.maxcare.kids.netfilter

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.netfilter.dns.BlockCategory

@OptIn(ExperimentalCoroutinesApi::class)
class BlockReporterTest {
    private val events = RecordingEvents()
    private var now = 1_000_000L
    private var notifications = 0

    private fun TestScope.reporter() = BlockReporter(
        events = events,
        notifier = { notifications++ },
        clock = { now },
        appScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    )

    @Test
    fun `a burst for one site gives one notification and one event with its address`() = runTest {
        val reporter = reporter()

        repeat(30) { reporter.onBlocked(BlockCategory.FILTERED, "m.example-adult.test") }

        assertEquals(1, notifications)
        assertEquals(listOf(EventType.SITE_BLOCKED_FILTERED), events.recorded)
        assertEquals(listOf("m.example-adult.test"), events.details)
    }

    @Test
    fun `different sites are each reported`() = runTest {
        val reporter = reporter()

        reporter.onBlocked(BlockCategory.FILTERED, "a.example.test")
        reporter.onBlocked(BlockCategory.FILTERED, "b.example.test")
        reporter.onBlocked(BlockCategory.GAMBLING, "1xbet.com")

        assertEquals(listOf("a.example.test", "b.example.test", "1xbet.com"), events.details)
        assertEquals(1, notifications)
    }

    @Test
    fun `notifications at most once a minute`() = runTest {
        val reporter = reporter()
        reporter.onBlocked(BlockCategory.FILTERED, "a.example.test")

        now += 59_000
        reporter.onBlocked(BlockCategory.FILTERED, "a.example.test")
        now += 1_000
        reporter.onBlocked(BlockCategory.FILTERED, "a.example.test")

        assertEquals(2, notifications)
    }

    @Test
    fun `the same site again after an hour is reported again`() = runTest {
        val reporter = reporter()
        reporter.onBlocked(BlockCategory.FILTERED, "a.example.test")

        now += 59 * 60_000
        reporter.onBlocked(BlockCategory.FILTERED, "a.example.test")
        now += 60_000
        reporter.onBlocked(BlockCategory.FILTERED, "a.example.test")

        assertEquals(2, events.recorded.size)
    }

    @Test
    fun `no more than 100 sites an hour, so the log cannot be flooded`() = runTest {
        val reporter = reporter()

        repeat(150) { reporter.onBlocked(BlockCategory.FILTERED, "site$it.example.test") }
        assertEquals(100, events.recorded.size)

        now += 60 * 60_000
        reporter.onBlocked(BlockCategory.FILTERED, "late.example.test")
        assertEquals(101, events.recorded.size)
    }

    @Test
    fun `a name that is not a plain domain is reported without it`() = runTest {
        reporter().onBlocked(BlockCategory.FILTERED, "weird_name.example.test")

        assertEquals(listOf(EventType.SITE_BLOCKED_FILTERED), events.recorded)
        assertEquals(listOf<String?>(null), events.details)
    }
}

class PrivateDnsMonitorTest {
    private val store = InMemoryStore()
    private val events = RecordingEvents()
    private var strictOn = false
    private val monitor = PrivateDnsMonitor(store, events) { strictOn }

    @Test
    fun `off records nothing`() = runTest {
        repeat(3) { monitor.check() }

        assertTrue(events.recorded.isEmpty())
    }

    @Test
    fun `turning private dns on is recorded once per change`() = runTest {
        monitor.check()
        strictOn = true
        repeat(3) { monitor.check() }
        strictOn = false
        monitor.check()
        strictOn = true
        monitor.check()

        assertEquals(listOf(EventType.PRIVATE_DNS_ON, EventType.PRIVATE_DNS_ON), events.recorded)
    }

    @Test
    fun `private dns already on before installation is still a bypass`() = runTest {
        strictOn = true

        monitor.check()

        assertEquals(listOf(EventType.PRIVATE_DNS_ON), events.recorded)
    }

    @Test
    fun `broken storage does not crash the check`() = runTest {
        store.broken = true
        strictOn = true

        monitor.check()
    }
}

private class RecordingEvents : EventRecorder {
    val recorded = mutableListOf<EventType>()
    val details = mutableListOf<String?>()

    override suspend fun record(type: EventType, detail: String?) {
        recorded += type
        details += detail
    }
}

private class InMemoryStore : SecureStore {
    private val values = mutableMapOf<String, ByteArray>()
    var broken = false

    override suspend fun read(key: String): ByteArray? {
        if (broken) throw SecureStoreException("broken", IOException())
        return values[key]
    }

    override suspend fun write(key: String, value: ByteArray) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}
