package uz.maxcare.kids.protection

import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.protection.DisableProtectionResult
import uz.maxcare.kids.core.protection.InMemoryProtectionShutdownSignal
import uz.maxcare.kids.core.protection.PasswordCheck
import uz.maxcare.kids.protection.password.FakeDeviceClock
import uz.maxcare.kids.protection.password.FakeSecureStore
import uz.maxcare.kids.protection.service.NotificationStateMonitor

class PasswordProtectedShutdownTest {
    private val service = FakeProtectionServiceStarter()
    private val password = FakeParentPassword()
    private val events = FakeEventRecorder()
    private val signal = InMemoryProtectionShutdownSignal()
    private val signals = mutableListOf<Unit>()

    private fun TestScope.shutdown(gateway: FakeDevicePolicyGateway) = PasswordProtectedShutdown(
        parentPassword = password,
        gateway = gateway,
        remover = ProtectionRemover(gateway, service, FakeNetworkFilter(), FakeDeviceClock()),
        events = events,
        shutdownSignal = signal,
        ioDispatcher = StandardTestDispatcher(testScheduler)
    ).also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            signal.shutdowns.collect { signals += it }
        }
    }

    private suspend fun TestScope.deviceOwner(): FakeDevicePolicyGateway {
        val gateway = FakeDevicePolicyGateway(deviceOwner = true)
        DevicePolicyProtection(
            gateway,
            service,
            NotificationStateMonitor(FakeSecureStore(), FakeEventRecorder()) { true },
            FakeNetworkFilter(),
            StandardTestDispatcher(testScheduler)
        )
            .applyPolicies()
        return gateway
    }

    @Test
    fun `correct password removes device owner protection`() = runTest {
        val gateway = deviceOwner()

        assertEquals(DisableProtectionResult.Disabled, shutdown(gateway).disable(pw()))

        assertFalse(gateway.isDeviceOwner())
        assertFalse(gateway.uninstallBlocked)
        assertTrue(gateway.restrictions.isEmpty())
        assertFalse(service.running)
    }

    @Test
    fun `correct password removes device admin`() = runTest {
        val gateway = FakeDevicePolicyGateway(adminActive = true)
        service.start()

        assertEquals(DisableProtectionResult.Disabled, shutdown(gateway).disable(pw()))

        assertFalse(gateway.isAdminActive())
        assertFalse(service.running)
    }

    @Test
    fun `wrong password removes nothing`() = runTest {
        val gateway = deviceOwner()
        password.result = PasswordCheck.Wrong(attemptsBeforeLockout = 4)

        assertEquals(
            DisableProtectionResult.Rejected(PasswordCheck.Wrong(4)),
            shutdown(gateway).disable(pw())
        )

        assertTrue(gateway.isDeviceOwner())
        assertTrue(gateway.uninstallBlocked)
        assertTrue(service.running)
    }

    @Test
    fun `lockout is passed through and removes nothing`() = runTest {
        val gateway = deviceOwner()
        password.result = PasswordCheck.LockedOut(5.minutes)

        assertEquals(
            DisableProtectionResult.Rejected(PasswordCheck.LockedOut(5.minutes)),
            shutdown(gateway).disable(pw())
        )
        assertTrue(gateway.isDeviceOwner())
    }

    @Test
    fun `inactive protection does not spend an attempt`() = runTest {
        val array = pw()

        assertEquals(
            DisableProtectionResult.NotActive,
            shutdown(FakeDevicePolicyGateway()).disable(array)
        )

        assertEquals(0, password.verifyCalls)
        assertTrue(array.all { it == '\u0000' })
    }

    @Test
    fun `parent disabling protection is recorded`() = runTest {
        shutdown(deviceOwner()).disable(pw())

        assertEquals(listOf(EventType.PROTECTION_DISABLED), events.recorded)
    }

    @Test
    fun `rejected password records nothing here`() = runTest {
        password.result = PasswordCheck.Wrong(attemptsBeforeLockout = 4)

        shutdown(deviceOwner()).disable(pw())

        assertTrue(events.recorded.isEmpty())
    }

    @Test
    fun `correct password tells the content filter to switch itself off`() = runTest {
        shutdown(deviceOwner()).disable(pw())

        assertEquals(1, signals.size)
    }

    @Test
    fun `wrong password, lockout and inactive protection keep the filter on`() = runTest {
        password.result = PasswordCheck.Wrong(attemptsBeforeLockout = 4)
        shutdown(deviceOwner()).disable(pw())
        password.result = PasswordCheck.LockedOut(5.minutes)
        shutdown(deviceOwner()).disable(pw())
        shutdown(FakeDevicePolicyGateway()).disable(pw())

        assertTrue(signals.isEmpty())
    }

    private fun pw() = "secret-password".toCharArray()
}
