package uz.maxcare.kids.location

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.location.LocationAccess.DENIED
import uz.maxcare.kids.location.LocationAccess.ON
import uz.maxcare.kids.location.LocationAccess.SWITCHED_OFF

class LocationStateMonitorTest {
    private val store = InMemoryStore()
    private val events = FakeEventRecorder()
    private val probe = FakeProbe()
    private val notice = FakeNotice()
    private val monitor = LocationStateMonitor(store, events, probe, notice)

    private suspend fun observe(vararg states: LocationAccess) = states.forEach {
        probe.access = it
        monitor.check()
    }

    @Test
    fun `first check only remembers the state`() = runTest {
        observe(SWITCHED_OFF)

        assertTrue(events.recorded.isEmpty())
    }

    @Test
    fun `switching location off and on is reported once each`() = runTest {
        observe(ON, SWITCHED_OFF, SWITCHED_OFF, SWITCHED_OFF, ON, ON)

        assertEquals(listOf(EventType.LOCATION_OFF, EventType.LOCATION_ON), events.recorded)
    }

    @Test
    fun `revoked permission is reported as denied`() = runTest {
        observe(ON, DENIED, ON)

        assertEquals(listOf(EventType.LOCATION_DENIED, EventType.LOCATION_ON), events.recorded)
    }

    @Test
    fun `granting permission in the setup wizard is not an event`() = runTest {
        observe(DENIED, ON, SWITCHED_OFF)

        assertEquals(listOf(EventType.LOCATION_OFF), events.recorded)
    }

    @Test
    fun `switch off while permission was never granted stays quiet`() = runTest {
        observe(DENIED, SWITCHED_OFF, ON)

        assertTrue(events.recorded.isEmpty())
    }

    @Test
    fun `moving between off and denied after a report is reported again`() = runTest {
        observe(ON, SWITCHED_OFF, DENIED, ON)

        assertEquals(
            listOf(EventType.LOCATION_OFF, EventType.LOCATION_DENIED, EventType.LOCATION_ON),
            events.recorded
        )
    }

    @Test
    fun `state survives a restart`() = runTest {
        observe(ON, SWITCHED_OFF)

        probe.access = ON
        LocationStateMonitor(store, events, probe, notice).check()

        assertEquals(listOf(EventType.LOCATION_OFF, EventType.LOCATION_ON), events.recorded)
    }

    @Test
    fun `child sees a notice while location is off and it goes away when back on`() = runTest {
        observe(ON, SWITCHED_OFF)
        assertEquals(SWITCHED_OFF, notice.shown)

        observe(ON)
        assertNull(notice.shown)
    }

    @Test
    fun `no denied notice before the setup wizard grants access`() = runTest {
        observe(DENIED)
        assertNull(notice.shown)

        observe(ON, DENIED)
        assertEquals(DENIED, notice.shown)
    }

    @Test
    fun `broken storage still shows the notice and does not crash`() = runTest {
        store.failWrites = true
        observe(ON, SWITCHED_OFF)

        assertEquals(SWITCHED_OFF, notice.shown)
    }

    @Test
    fun `damaged stored state counts as a first check`() = runTest {
        store.values["location_last_state"] = "garbage".encodeToByteArray()

        observe(SWITCHED_OFF)

        assertTrue(events.recorded.isEmpty())
    }
}

internal class FakeProbe : LocationAccessProbe {
    var access = ON
    override fun current() = access
}

internal class FakeNotice : LocationNotice {
    var shown: LocationAccess? = null
    override fun show(access: LocationAccess) {
        shown = access
    }

    override fun hide() {
        shown = null
    }
}

internal class FakeEventRecorder : EventRecorder {
    val recorded = mutableListOf<EventType>()
    override suspend fun record(type: EventType, detail: String?) {
        recorded += type
    }
}
