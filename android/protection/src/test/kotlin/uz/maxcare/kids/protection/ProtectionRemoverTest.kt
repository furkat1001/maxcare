package uz.maxcare.kids.protection

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionRemoverTest {
    private val service = FakeProtectionServiceStarter()

    @Test
    fun `device owner is fully removed`() = runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        DevicePolicyProtection(gateway, service, StandardTestDispatcher(testScheduler))
            .applyPolicies()
        assertTrue(service.running)

        assertTrue(ProtectionRemover(gateway, service).removeAll())

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

        assertTrue(ProtectionRemover(gateway, service).removeAll())

        assertFalse(gateway.isAdminActive())
        assertFalse(service.running)
    }

    @Test
    fun `nothing to remove without protection`() {
        val gateway = FakeDevicePolicyGateway()

        assertFalse(ProtectionRemover(gateway, service).removeAll())
        assertEquals(0, gateway.policyCalls)
    }
}
