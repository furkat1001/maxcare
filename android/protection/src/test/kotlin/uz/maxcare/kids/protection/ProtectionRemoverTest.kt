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
    private val filter = FakeNetworkFilter()

    @Test
    fun `device owner is fully removed`() = runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        DevicePolicyProtection(
            gateway,
            service,
            NotificationStateMonitor(FakeSecureStore(), FakeEventRecorder()) { true },
            filter,
            StandardTestDispatcher(testScheduler)
        )
            .applyPolicies()
        assertTrue(service.running)

        assertTrue(ProtectionRemover(gateway, service, filter, clock).removeAll())

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

        assertTrue(ProtectionRemover(gateway, service, filter, clock).removeAll())

        assertFalse(gateway.isAdminActive())
        assertFalse(service.running)
    }

    @Test
    fun `nothing to remove without protection`() {
        val gateway = FakeDevicePolicyGateway()

        assertFalse(ProtectionRemover(gateway, service, filter, clock).removeAll())
        assertEquals(0, gateway.policyCalls)
    }

    @Test
    fun `remembers its own removal for a minute`() {
        val gateway = FakeDevicePolicyGateway(adminActive = true)
        val remover = ProtectionRemover(gateway, service, filter, clock)
        assertFalse(remover.removedRecently())

        remover.removeAll()
        assertTrue(remover.removedRecently())

        clock.advance(61.seconds)
        assertFalse(remover.removedRecently())
    }

    @Test
    fun `removal turns off the always-on vpn and stops the filter`() = runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        DevicePolicyProtection(
            gateway,
            service,
            NotificationStateMonitor(FakeSecureStore(), FakeEventRecorder()) { true },
            filter,
            StandardTestDispatcher(testScheduler)
        ).applyPolicies()
        assertTrue(gateway.alwaysOnVpn)

        ProtectionRemover(gateway, service, filter, clock).removeAll()

        assertFalse(gateway.alwaysOnVpn)
        assertFalse(filter.running)
    }
}
