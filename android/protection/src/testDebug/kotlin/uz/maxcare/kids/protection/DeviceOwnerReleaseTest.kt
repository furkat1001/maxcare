package uz.maxcare.kids.protection

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOwnerReleaseTest {
    @Test
    fun `release lifts policies and then device owner`() = runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        DevicePolicyProtection(gateway, StandardTestDispatcher(testScheduler)).applyPolicies()

        assertTrue(DeviceOwnerRelease(gateway).release())

        assertFalse(gateway.uninstallBlocked)
        assertTrue(gateway.restrictions.isEmpty())
        assertFalse(gateway.isDeviceOwner())
        assertFalse(gateway.isAdminActive())
    }

    @Test
    fun `release without device owner does nothing`() {
        val gateway = FakeDevicePolicyGateway(adminActive = true)

        assertFalse(DeviceOwnerRelease(gateway).release())
        assertEquals(0, gateway.policyCalls)
    }
}
