package uz.maxcare.kids.setup.ui

import android.content.Intent
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uz.maxcare.kids.core.filter.NetworkFilter
import uz.maxcare.kids.core.location.LocationScheduler
import uz.maxcare.kids.core.pairing.DevicePairing
import uz.maxcare.kids.core.pairing.PairingResult
import uz.maxcare.kids.core.pairing.UnpairResult
import uz.maxcare.kids.core.protection.ChangePasswordResult
import uz.maxcare.kids.core.protection.DeviceAdminActivation
import uz.maxcare.kids.core.protection.DeviceProtection
import uz.maxcare.kids.core.protection.DisableProtectionResult
import uz.maxcare.kids.core.protection.ParentPassword
import uz.maxcare.kids.core.protection.PasswordCheck
import uz.maxcare.kids.core.protection.PasswordRecovery
import uz.maxcare.kids.core.protection.ProtectionMode
import uz.maxcare.kids.core.protection.ProtectionShutdown
import uz.maxcare.kids.core.protection.RecoveryResult
import uz.maxcare.kids.core.protection.SetPasswordResult
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.usage.AppUsageScheduler

@OptIn(ExperimentalCoroutinesApi::class)
class KidsViewModelTest {
    private val pairing = FakePairing()
    private val password = FakePassword()
    private val protection = FakeProtection()
    private val shutdown = FakeShutdown()
    private val recovery = FakeRecovery()
    private val store = FakeStore()
    private val environment = FakeEnvironment()
    private val filter = FakeFilter()
    private val scheduler = FakeScheduler()
    private val usageScheduler = FakeUsageScheduler()
    private var resets = 0

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = KidsViewModel(
        pairing,
        password,
        protection,
        shutdown,
        recovery,
        NoAdminActivation,
        filter,
        scheduler,
        usageScheduler,
        store,
        environment
    ) { resets++ }.also { it.refresh() }

    private val KidsViewModel.screen get() = state.value.screen
    private val KidsViewModel.message get() = state.value.message

    private fun setUpFully(mode: ProtectionMode = ProtectionMode.DEVICE_ADMIN): KidsViewModel {
        store.values["setup_disclosure_accepted"] = "1".encodeToByteArray()
        pairing.paired = true
        password.set = true
        protection.activeMode = mode
        return viewModel()
    }

    @Test
    fun `fresh install starts with the disclosure`() {
        assertEquals(KidsScreen.Setup(SetupStep.DISCLOSURE, 1, 8), viewModel().screen)
    }

    @Test
    fun `background hint adds a step on aggressive firmware`() {
        environment.backgroundHint = true

        assertEquals(KidsScreen.Setup(SetupStep.DISCLOSURE, 1, 9), viewModel().screen)
    }

    @Test
    fun `steps follow each other`() {
        val vm = viewModel()

        vm.acceptDisclosure()
        assertEquals(KidsScreen.Setup(SetupStep.PAIRING, 2, 8), vm.screen)

        vm.submitPairingCode("12345678")
        assertEquals(KidsScreen.Setup(SetupStep.PASSWORD, 3, 8), vm.screen)

        vm.submitNewPassword("secret-1", "secret-1")
        assertEquals(KidsScreen.Setup(SetupStep.DEVICE_ADMIN, 4, 8), vm.screen)

        protection.activeMode = ProtectionMode.DEVICE_ADMIN
        environment.notifications = false
        environment.location = false
        filter.permitted = false
        vm.onDeviceAdminResult()
        assertEquals(KidsScreen.Setup(SetupStep.WEB_FILTER, 5, 8, canTurnOff = true), vm.screen)

        filter.permitted = true
        vm.onWebFilterResult()
        assertEquals(KidsScreen.Setup(SetupStep.NOTIFICATIONS, 6, 8, canTurnOff = true), vm.screen)

        environment.notifications = true
        vm.onNotificationPermissionResult()
        assertEquals(KidsScreen.Setup(SetupStep.LOCATION, 7, 8, canTurnOff = true), vm.screen)

        environment.location = true
        environment.usageAccess = false
        vm.onLocationPermissionResult()
        assertEquals(KidsScreen.Setup(SetupStep.USAGE_ACCESS, 8, 8, canTurnOff = true), vm.screen)
        assertEquals(1, scheduler.scheduled)

        environment.usageAccess = true
        vm.onUsageAccessResult()
        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_ADMIN), vm.screen)
        assertEquals(1, usageScheduler.scheduled)
    }

    @Test
    fun `setup resumes where it stopped`() {
        store.values["setup_disclosure_accepted"] = "1".encodeToByteArray()
        pairing.paired = true

        assertEquals(KidsScreen.Setup(SetupStep.PASSWORD, 3, 8), viewModel().screen)
    }

    @Test
    fun `device owner skips the device admin step`() {
        store.values["setup_disclosure_accepted"] = "1".encodeToByteArray()
        pairing.paired = true
        password.set = true
        protection.activeMode = ProtectionMode.DEVICE_OWNER

        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_OWNER), viewModel().screen)
    }

    @Test
    fun `pairing errors keep the step and explain`() {
        store.values["setup_disclosure_accepted"] = "1".encodeToByteArray()
        val vm = viewModel()
        mapOf(
            PairingResult.InvalidFormat to UiMessage.InvalidCodeFormat,
            PairingResult.InvalidCode to UiMessage.InvalidCode,
            PairingResult.TooManyAttempts to UiMessage.TooManyAttempts,
            PairingResult.NoConnection to UiMessage.NoConnection,
            PairingResult.InsecureConnection to UiMessage.InsecureConnection,
            PairingResult.ServerError to UiMessage.ServerError
        ).forEach { (result, message) ->
            pairing.result = result
            vm.submitPairingCode("12345678")
            assertEquals(SetupStep.PAIRING, (vm.screen as KidsScreen.Setup).step)
            assertEquals(message, vm.message)
        }
    }

    @Test
    fun `different passwords are not saved`() {
        store.values["setup_disclosure_accepted"] = "1".encodeToByteArray()
        pairing.paired = true
        val vm = viewModel()

        vm.submitNewPassword("secret-1", "secret-2")

        assertEquals(UiMessage.PasswordsDiffer, vm.message)
        assertFalse(password.set)
    }

    @Test
    fun `short password is explained`() {
        store.values["setup_disclosure_accepted"] = "1".encodeToByteArray()
        pairing.paired = true
        password.setResult = SetPasswordResult.TooShort
        val vm = viewModel()

        vm.submitNewPassword("123", "123")

        assertEquals(UiMessage.PasswordTooShort, vm.message)
    }

    @Test
    fun `declined device admin is explained`() {
        store.values["setup_disclosure_accepted"] = "1".encodeToByteArray()
        pairing.paired = true
        password.set = true
        val vm = viewModel()

        vm.onDeviceAdminResult()

        assertEquals(KidsScreen.Setup(SetupStep.DEVICE_ADMIN, 4, 8), vm.screen)
        assertEquals(UiMessage.AdminNotActivated, vm.message)
    }

    @Test
    fun `protection can be turned off from a step asking to re-enable something`() {
        // Ребёнок удалил VPN-профиль: защита работает, мастер просит включить фильтр снова.
        filter.permitted = false
        val vm = setUpFully()
        val step = vm.screen as KidsScreen.Setup
        assertEquals(SetupStep.WEB_FILTER, step.step)
        assertTrue(step.canTurnOff)

        vm.openDisableProtection()
        assertEquals(KidsScreen.DisableProtection, vm.screen)

        vm.back()
        assertEquals(SetupStep.WEB_FILTER, (vm.screen as KidsScreen.Setup).step)
    }

    @Test
    fun `nothing to turn off before the device admin is on`() {
        store.values["setup_disclosure_accepted"] = "1".encodeToByteArray()
        pairing.paired = true
        password.set = true

        val step = viewModel().screen as KidsScreen.Setup

        assertEquals(SetupStep.DEVICE_ADMIN, step.step)
        assertFalse(step.canTurnOff)
    }

    @Test
    fun `declined vpn consent is explained`() {
        filter.permitted = false
        val vm = setUpFully()

        vm.onWebFilterResult()

        assertEquals(SetupStep.WEB_FILTER, (vm.screen as KidsScreen.Setup).step)
        assertEquals(UiMessage.FilterNotAllowed, vm.message)
    }

    @Test
    fun `denied notifications are explained`() {
        environment.notifications = false
        val vm = setUpFully()

        vm.onNotificationPermissionResult()

        assertEquals(UiMessage.NotificationsDenied, vm.message)
    }

    @Test
    fun `location permission is asked for before setup finishes`() {
        environment.location = false
        val vm = setUpFully()

        val step = vm.screen as KidsScreen.Setup
        assertEquals(SetupStep.LOCATION, step.step)
        assertNull(vm.message)
    }

    @Test
    fun `granting location starts the periodic collection`() {
        environment.location = false
        val vm = setUpFully()

        environment.location = true
        vm.onLocationPermissionResult()

        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_ADMIN), vm.screen)
        assertEquals(1, scheduler.scheduled)
    }

    @Test
    fun `location step stays and schedules nothing until granted`() {
        environment.location = false
        val vm = setUpFully()

        vm.onLocationPermissionResult()

        assertEquals(SetupStep.LOCATION, (vm.screen as KidsScreen.Setup).step)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun `usage access is asked for before setup finishes`() {
        environment.usageAccess = false
        val vm = setUpFully()

        assertEquals(SetupStep.USAGE_ACCESS, (vm.screen as KidsScreen.Setup).step)
        assertNull(vm.message)
    }

    @Test
    fun `granting usage access starts sending app usage`() {
        environment.usageAccess = false
        val vm = setUpFully()

        environment.usageAccess = true
        vm.onUsageAccessResult()

        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_ADMIN), vm.screen)
        assertEquals(1, usageScheduler.scheduled)
    }

    @Test
    fun `usage access step stays and schedules nothing until granted`() {
        environment.usageAccess = false
        val vm = setUpFully()

        vm.onUsageAccessResult()

        assertEquals(SetupStep.USAGE_ACCESS, (vm.screen as KidsScreen.Setup).step)
        assertEquals(0, usageScheduler.scheduled)
    }

    @Test
    fun `revoked usage access brings the step back`() {
        val vm = setUpFully()
        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_ADMIN), vm.screen)

        environment.usageAccess = false
        vm.refresh()

        val step = vm.screen as KidsScreen.Setup
        assertEquals(SetupStep.USAGE_ACCESS, step.step)
        assertEquals(true, step.canTurnOff)
    }

    @Test
    fun `wrong password when disabling shows attempts left`() {
        val vm = setUpFully()
        vm.openDisableProtection()
        shutdown.result = DisableProtectionResult.Rejected(PasswordCheck.Wrong(3))

        vm.submitDisable("wrong")

        assertEquals(KidsScreen.DisableProtection, vm.screen)
        assertEquals(UiMessage.WrongPassword(3), vm.message)
    }

    @Test
    fun `lockout minutes are rounded up`() {
        val vm = setUpFully()
        vm.openDisableProtection()
        shutdown.result = DisableProtectionResult.Rejected(PasswordCheck.LockedOut(30.seconds))

        vm.submitDisable("wrong")

        assertEquals(UiMessage.LockedOut(1), vm.message)
    }

    @Test
    fun `disabled protection stays on its screen`() {
        val vm = setUpFully()
        vm.openDisableProtection()

        vm.submitDisable("secret-1")
        vm.refresh()

        assertEquals(KidsScreen.Disabled, vm.screen)
    }

    private fun disabledByParent(): KidsViewModel {
        val vm = setUpFully()
        vm.openDisableProtection()
        vm.submitDisable("secret-1")
        protection.activeMode = ProtectionMode.NONE
        return vm
    }

    @Test
    fun `unpair after disabling protection removes the phone and wipes the app`() {
        val vm = disabledByParent()

        vm.unpair()

        assertEquals(1, pairing.unpairCalls)
        assertEquals(1, resets)
    }

    @Test
    fun `unpair is ignored outside the disabled screen`() {
        val vm = setUpFully()

        vm.unpair()

        assertEquals(0, pairing.unpairCalls)
        assertEquals(0, resets)
    }

    @Test
    fun `unpair is ignored while protection is still active`() {
        val vm = disabledByParent()
        protection.activeMode = ProtectionMode.DEVICE_ADMIN

        vm.unpair()

        assertEquals(0, pairing.unpairCalls)
        assertEquals(0, resets)
    }

    @Test
    fun `failed unpair keeps the data and explains why`() {
        val vm = disabledByParent()
        pairing.unpairResult = UnpairResult.NoConnection

        vm.unpair()

        assertEquals(0, resets)
        assertEquals(UiMessage.NoConnection, vm.message)
        assertEquals(KidsScreen.Disabled, vm.screen)
    }

    @Test
    fun `refresh does not interrupt password entry`() {
        val vm = setUpFully()
        vm.openPasswordRecovery()

        vm.refresh()

        assertEquals(KidsScreen.RecoverPassword, vm.screen)
    }

    @Test
    fun `successful recovery returns home with a confirmation`() {
        val vm = setUpFully()
        vm.openPasswordRecovery()

        vm.submitRecovery("12345678", "new-secret", "new-secret")

        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_ADMIN), vm.screen)
        assertEquals(UiMessage.PasswordReset, vm.message)
    }

    @Test
    fun `wrong recovery code shows attempts left`() {
        val vm = setUpFully()
        vm.openPasswordRecovery()
        recovery.result = RecoveryResult.InvalidCode(2)

        vm.submitRecovery("12345678", "new-secret", "new-secret")

        assertEquals(UiMessage.WrongRecoveryCode(2), vm.message)
    }

    @Test
    fun `damaged storage is reported instead of crashing`() {
        store.failWrites = true
        val vm = viewModel()

        vm.acceptDisclosure()

        assertEquals(UiMessage.StorageDamaged, vm.message)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `back returns home and clears the message`() {
        val vm = setUpFully()
        vm.openDisableProtection()
        shutdown.result = DisableProtectionResult.Rejected(PasswordCheck.Wrong(3))
        vm.submitDisable("wrong")

        vm.back()

        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_ADMIN), vm.screen)
        assertNull(vm.message)
    }

    private class FakePairing : DevicePairing {
        var paired = false
        var result: PairingResult = PairingResult.Paired

        override suspend fun isPaired() = paired

        override suspend fun pair(code: String): PairingResult {
            if (result == PairingResult.Paired) paired = true
            return result
        }

        var unpairResult: UnpairResult = UnpairResult.Unpaired
        var unpairCalls = 0

        override suspend fun unpair(): UnpairResult {
            unpairCalls++
            return unpairResult
        }
    }

    private class FakePassword : ParentPassword {
        var set = false
        var setResult: SetPasswordResult = SetPasswordResult.Saved

        override suspend fun isSet() = set

        override suspend fun setInitial(password: CharArray): SetPasswordResult {
            if (setResult == SetPasswordResult.Saved) set = true
            return setResult
        }

        override suspend fun verify(password: CharArray) = PasswordCheck.Correct

        override suspend fun change(current: CharArray, new: CharArray) =
            ChangePasswordResult.Changed

        override suspend fun lockoutRemaining() = Duration.ZERO
    }

    private class FakeProtection : DeviceProtection {
        var activeMode = ProtectionMode.NONE
        private val _mode = MutableStateFlow(ProtectionMode.NONE)
        override val mode: StateFlow<ProtectionMode> = _mode

        override suspend fun applyPolicies(): ProtectionMode {
            _mode.value = activeMode
            return activeMode
        }
    }

    private class FakeShutdown : ProtectionShutdown {
        var result: DisableProtectionResult = DisableProtectionResult.Disabled

        override suspend fun disable(password: CharArray) = result
    }

    private class FakeRecovery : PasswordRecovery {
        var result: RecoveryResult = RecoveryResult.Reset

        override suspend fun reset(code: String, newPassword: CharArray) = result
    }

    private object NoAdminActivation : DeviceAdminActivation {
        override fun activationIntent(): Intent = error("Not used in unit tests")
    }

    private class FakeStore : SecureStore {
        val values = mutableMapOf<String, ByteArray>()
        var failWrites = false

        override suspend fun read(key: String) = values[key]

        override suspend fun write(key: String, value: ByteArray) {
            if (failWrites) throw SecureStoreException("broken", IOException())
            values[key] = value
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }

    private class FakeFilter : NetworkFilter {
        var permitted = true

        override fun isPermitted() = permitted

        override fun permissionIntent(): Intent? = null

        override fun start() = Unit

        override fun stop() = Unit

        override suspend fun checkBypasses() = Unit
    }

    private class FakeEnvironment : SetupEnvironment {
        var notifications = true
        var location = true
        var usageAccess = true
        var backgroundHint = false

        override fun notificationsAllowed() = notifications

        override fun locationAllowed() = location

        override fun usageAccessAllowed() = usageAccess

        override fun needsBackgroundHint() = backgroundHint
    }

    private class FakeUsageScheduler : AppUsageScheduler {
        var scheduled = 0

        override fun schedulePeriodic() {
            scheduled++
        }
    }

    private class FakeScheduler : LocationScheduler {
        var scheduled = 0

        override fun schedulePeriodic() {
            scheduled++
        }
    }
}
