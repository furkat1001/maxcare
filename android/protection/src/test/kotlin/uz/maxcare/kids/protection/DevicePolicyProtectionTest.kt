package uz.maxcare.kids.protection

import android.os.UserManager
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.protection.ProtectionMode
import uz.maxcare.kids.protection.password.FakeSecureStore
import uz.maxcare.kids.protection.service.NotificationStateMonitor

class DevicePolicyProtectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val service = FakeProtectionServiceStarter()

    private val events = FakeEventRecorder()
    private val filter = FakeNetworkFilter()
    private var notificationsVisible = true
    private val monitor =
        NotificationStateMonitor(FakeSecureStore(), events) { notificationsVisible }

    private fun protection(gateway: FakeDevicePolicyGateway) =
        DevicePolicyProtection(gateway, service, monitor, filter, dispatcher)

    @Test
    fun `without admin no policies are applied and no notification is shown`() = testScope.runTest {
        val gateway = FakeDevicePolicyGateway()
        val protection = protection(gateway)

        assertEquals(ProtectionMode.NONE, protection.applyPolicies())
        assertEquals(ProtectionMode.NONE, protection.mode.value)
        assertEquals(0, gateway.policyCalls)
        assertFalse(service.running)
    }

    @Test
    fun `device admin shows the notification without device owner policies`() = testScope.runTest {
        val gateway = FakeDevicePolicyGateway(adminActive = true)
        val protection = protection(gateway)

        assertEquals(ProtectionMode.DEVICE_ADMIN, protection.applyPolicies())
        assertEquals(ProtectionMode.DEVICE_ADMIN, protection.mode.value)
        assertEquals(0, gateway.policyCalls)
        assertTrue(service.running)
    }

    @Test
    fun `device owner blocks uninstall, safe boot and factory reset`() = testScope.runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        val protection = protection(gateway)

        assertEquals(ProtectionMode.DEVICE_OWNER, protection.applyPolicies())
        assertEquals(ProtectionMode.DEVICE_OWNER, protection.mode.value)
        assertTrue(gateway.uninstallBlocked)
        assertEquals(
            setOf(
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_FACTORY_RESET,
                UserManager.DISALLOW_CONFIG_VPN
            ),
            gateway.restrictions
        )
    }

    @Test
    fun `device owner locks notification permission and shows the notification`() =
        testScope.runTest {
            val gateway = FakeDevicePolicyGateway(deviceOwner = true)

            protection(gateway).applyPolicies()

            assertTrue(gateway.notificationPermissionLocked)
            assertTrue(service.running)
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
        assertTrue(service.running)
    }

    @Test
    fun `notification goes away when protection is removed`() = testScope.runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        val protection = protection(gateway)
        protection.applyPolicies()

        gateway.deviceOwner = false

        assertEquals(ProtectionMode.NONE, protection.applyPolicies())
        assertFalse(gateway.isDeviceOwner())
        assertFalse(service.running)
    }

    @Test
    fun `notifications turned off soon after setup are recorded`() = testScope.runTest {
        // Как на Samsung: служба стартует до шага «Разрешить уведомления», родитель разрешает,
        // ребёнок через пару минут выключает — раньше 15 минут, до проверки службы.
        val protection = protection(FakeDevicePolicyGateway(adminActive = true))
        notificationsVisible = false
        protection.applyPolicies() // шаг «Администратор»: уведомления ещё не разрешены
        notificationsVisible = true
        protection.applyPolicies() // шаг «Уведомления»: разрешены
        notificationsVisible = false
        protection.applyPolicies() // открытие приложения или загрузка: выключены

        assertEquals(listOf(EventType.NOTIFICATIONS_OFF), events.recorded)
    }

    @Test
    fun `without protection notifications are not watched`() = testScope.runTest {
        notificationsVisible = true
        protection(FakeDevicePolicyGateway()).applyPolicies()
        notificationsVisible = false
        protection(FakeDevicePolicyGateway()).applyPolicies()

        assertTrue(events.recorded.isEmpty())
    }

    @Test
    fun `device owner turns the filter into an always-on vpn`() = testScope.runTest {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)

        protection(gateway).applyPolicies()

        assertTrue(gateway.alwaysOnVpn)
        assertTrue(filter.running)
    }

    @Test
    fun `device admin starts the filter only with vpn consent`() = testScope.runTest {
        filter.permitted = false
        protection(FakeDevicePolicyGateway(adminActive = true)).applyPolicies()
        assertFalse(filter.running)

        filter.permitted = true
        protection(FakeDevicePolicyGateway(adminActive = true)).applyPolicies()
        assertTrue(filter.running)
        assertEquals(2, filter.bypassChecks)
    }

    @Test
    fun `without protection the filter is stopped and not checked`() = testScope.runTest {
        filter.start()

        protection(FakeDevicePolicyGateway()).applyPolicies()

        assertFalse(filter.running)
        assertEquals(0, filter.bypassChecks)
    }
}
