package uz.maxcare.kids.protection

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOwnerReleaseTest {
    private val service = FakeProtectionServiceStarter()

    @Test
    fun `release lifts policies, device owner and the notification`() = runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        DevicePolicyProtection(
            gateway,
            service,
            StandardTestDispatcher(testScheduler)
        ).applyPolicies()
        assertTrue(service.running)

        assertTrue(DeviceOwnerRelease(gateway, service).release())

        assertFalse(gateway.uninstallBlocked)
        assertTrue(gateway.restrictions.isEmpty())
        assertFalse(gateway.isDeviceOwner())
        assertFalse(gateway.isAdminActive())
        assertFalse(service.running)
    }

    @Test
    fun `release without device owner does nothing`() {
        val gateway = FakeDevicePolicyGateway(adminActive = true)

        assertFalse(DeviceOwnerRelease(gateway, service).release())
        assertEquals(0, gateway.policyCalls)
    }
}
