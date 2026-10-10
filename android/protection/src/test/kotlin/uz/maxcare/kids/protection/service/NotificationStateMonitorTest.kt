package uz.maxcare.kids.protection.service

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.protection.FakeEventRecorder
import uz.maxcare.kids.protection.password.FakeSecureStore

class NotificationStateMonitorTest {
    private val store = FakeSecureStore()
    private val events = FakeEventRecorder()
    private var visible = true
    private val monitor = NotificationStateMonitor(store, events) { visible }

    @Test
    fun `hidden notifications at the very first check are not an event`() = runTest {
        // Так было на Samsung: служба запустилась до шага «Разрешить уведомления».
        visible = false

        monitor.check()
        visible = true
        monitor.check()

        assertTrue(events.recorded.isEmpty())
    }

    @Test
    fun `visible notifications record nothing`() = runTest {
        repeat(3) { monitor.check() }

        assertTrue(events.recorded.isEmpty())
    }

    @Test
    fun `turning notifications off is recorded once`() = runTest {
        monitor.check()
        visible = false

        repeat(3) { monitor.check() }

        assertEquals(listOf(EventType.NOTIFICATIONS_OFF), events.recorded)
    }

    @Test
    fun `turning them off again after re-enabling is recorded again`() = runTest {
        monitor.check()
        visible = false
        monitor.check()
        visible = true
        monitor.check()
        visible = false
        monitor.check()

        assertEquals(
            listOf(EventType.NOTIFICATIONS_OFF, EventType.NOTIFICATIONS_OFF),
            events.recorded
        )
    }

    @Test
    fun `broken storage does not crash the check`() = runTest {
        store.broken += "notifications_last_state"

        monitor.check()
    }

    @Test
    fun `simultaneous checks record one event`() = runTest {
        val yielding = YieldingStore(store)
        val monitor = NotificationStateMonitor(yielding, events) { visible }
        monitor.check()
        visible = false

        listOf(async { monitor.check() }, async { monitor.check() }).awaitAll()

        assertEquals(listOf(EventType.NOTIFICATIONS_OFF), events.recorded)
    }

    /**
     * Отдаёт управление сразу после чтения: обе проверки успевают прочитать старое состояние
     * до того, как какая-то из них запишет новое, — как на телефоне.
     */
    private class YieldingStore(private val inner: SecureStore) : SecureStore by inner {
        override suspend fun read(key: String): ByteArray? {
            val value = inner.read(key)
            yield()
            return value
        }
    }
}
