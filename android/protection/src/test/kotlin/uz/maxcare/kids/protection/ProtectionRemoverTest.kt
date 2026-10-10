package uz.maxcare.kids.protection

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.protection.password.FakeDeviceClock
import uz.maxcare.kids.protection.password.FakeSecureStore
import uz.maxcare.kids.protection.service.NotificationStateMonitor

class ProtectionRemoverTest {
    private val service = FakeProtectionServiceStarter()
    private val clock = FakeDeviceClock()

    @Test
    fun `device owner is fully removed`() = runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        DevicePolicyProtection(
            gateway,
            service,
            NotificationStateMonitor(FakeSecureStore(), FakeEventRecorder()) { true },
            StandardTestDispatcher(testScheduler)
        )
            .applyPolicies()
        assertTrue(service.running)

        assertTrue(ProtectionRemover(gateway, service, clock).removeAll())

        assertFalse(gateway.uninstallBlocked)
        assertTrue(gateway.restrictions.isEmpty())
        assertFalse(gateway.isDeviceOwner())
        assertFalse(gateway.isAdminActive())
        assertFalse(service.running)
    }

    @Test
    fun `device admin is removed`() {
        val gateway = FakeDevicePolicyGateway(adminActive = true)
        service.start()

        assertTrue(ProtectionRemover(gateway, service, clock).removeAll())

        assertFalse(gateway.isAdminActive())
        assertFalse(service.running)
    }

    @Test
    fun `nothing to remove without protection`() {
        val gateway = FakeDevicePolicyGateway()

        assertFalse(ProtectionRemover(gateway, service, clock).removeAll())
        assertEquals(0, gateway.policyCalls)
    }

    @Test
    fun `remembers its own removal for a minute`() {
        val gateway = FakeDevicePolicyGateway(adminActive = true)
        val remover = ProtectionRemover(gateway, service, clock)
        assertFalse(remover.removedRecently())

        remover.removeAll()
        assertTrue(remover.removedRecently())

        clock.advance(61.seconds)
        assertFalse(remover.removedRecently())
    }
}
