package uz.maxcare.kids.protection

import android.os.UserManager
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.protection.ProtectionMode

class DevicePolicyProtectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private fun protection(gateway: FakeDevicePolicyGateway) =
        DevicePolicyProtection(gateway, dispatcher)

    @Test
    fun `without admin no policies are applied`() = testScope.runTest {
        val gateway = FakeDevicePolicyGateway()
        val protection = protection(gateway)

        assertEquals(ProtectionMode.NONE, protection.applyPolicies())
        assertEquals(ProtectionMode.NONE, protection.mode.value)
        assertEquals(0, gateway.policyCalls)
    }

    @Test
    fun `device admin does not try device owner policies`() = testScope.runTest {
        val gateway = FakeDevicePolicyGateway(adminActive = true)
        val protection = protection(gateway)

        assertEquals(ProtectionMode.DEVICE_ADMIN, protection.applyPolicies())
        assertEquals(ProtectionMode.DEVICE_ADMIN, protection.mode.value)
        assertEquals(0, gateway.policyCalls)
    }

    @Test
    fun `device owner blocks uninstall, safe boot and factory reset`() = testScope.runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        val protection = protection(gateway)

        assertEquals(ProtectionMode.DEVICE_OWNER, protection.applyPolicies())
        assertEquals(ProtectionMode.DEVICE_OWNER, protection.mode.value)
        assertTrue(gateway.uninstallBlocked)
        assertEquals(
            setOf(UserManager.DISALLOW_SAFE_BOOT, UserManager.DISALLOW_FACTORY_RESET),
            gateway.restrictions
        )
    }

    @Test
    fun `applying twice keeps the same state`() = testScope.runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        val protection = protection(gateway)

        protection.applyPolicies()
        val restrictionsAfterFirst = gateway.restrictions.toSet()
        protection.applyPolicies()

        assertEquals(restrictionsAfterFirst, gateway.restrictions)
        assertTrue(gateway.uninstallBlocked)
        assertEquals(ProtectionMode.DEVICE_OWNER, protection.mode.value)
    }

    @Test
    fun `mode follows device owner being removed`() = testScope.runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        val protection = protection(gateway)
        protection.applyPolicies()

        gateway.deviceOwner = false

        assertEquals(ProtectionMode.NONE, protection.applyPolicies())
        assertFalse(gateway.isDeviceOwner())
    }
}
