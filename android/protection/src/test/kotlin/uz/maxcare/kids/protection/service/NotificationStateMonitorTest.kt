package uz.maxcare.kids.protection.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.events.EventType
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
}
