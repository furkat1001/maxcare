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
    fun `visible notifications record nothing`() = runTest {
        monitor.check()

        assertTrue(events.recorded.isEmpty())
    }

    @Test
    fun `turning notifications off is recorded once`() = runTest {
        visible = false

        repeat(3) { monitor.check() }

        assertEquals(listOf(EventType.NOTIFICATIONS_OFF), events.recorded)
    }

    @Test
    fun `turning them off again after re-enabling is recorded again`() = runTest {
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
        visible = false
        store.broken += "notifications_off_reported"

        monitor.check()
    }
}
