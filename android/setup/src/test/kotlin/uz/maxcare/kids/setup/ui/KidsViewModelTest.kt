package uz.maxcare.kids.setup.ui

import android.content.Intent
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
import uz.maxcare.kids.core.protection.TimedTrustedSettingsWindow
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
    private var now = 0L
    private val trustedSettings = TimedTrustedSettingsWindow { now }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        collector.cancel()
        Dispatchers.resetMain()
    }

    private val collector = CoroutineScope(UnconfinedTestDispatcher())
    private val launched = mutableListOf<SystemLaunch>()

    /** Что открыла бы Activity. */
    private fun KidsViewModel.collectLaunches() = also { vm ->
        collector.launch { vm.launches.collect { launched += it } }
    }

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
        environment,
        { resets++ },
        trustedSettings
    ).also { it.refresh() }

    private val KidsViewModel.screen get() = state.value.screen
    private val KidsViewModel.message get() = state.value.message

    private fun setUpFully(mode: ProtectionMode = ProtectionMode.DEVICE_ADMIN): KidsViewModel {
        store.values["setup_disclosure_accepted"] = "1".encodeToByteArray()
        pairing.paired = true
        password.set = true
        protection.activeMode = mode
        return viewModel()
    }

    private val allSteps = listOf(
        SetupStep.DEVICE_ADMIN,
        SetupStep.WEB_FILTER,
        SetupStep.NOTIFICATIONS,
        SetupStep.LOCATION,
        SetupStep.USAGE_ACCESS,
        SetupStep.ACCESSIBILITY
    )

    private val KidsViewModel.permissions get() = screen as KidsScreen.Permissions

    private fun connected(): KidsViewModel {
        store.values["setup_disclosure_accepted"] = "1".encodeToByteArray()
        pairing.paired = true
        password.set = true
        return viewModel()
    }

    @Test
    fun `fresh install starts with the connect screen`() {
        assertEquals(KidsScreen.Connect(paired = false, needsPassword = true), viewModel().screen)
    }

    @Test
    fun `one tap connects the phone, saves the password and opens the permissions`() {
        val vm = viewModel()

        vm.submitConnect("12345678", "secret-1", "secret-1")

        assertTrue(pairing.paired)
        assertTrue(password.set)
        assertEquals(KidsScreen.Permissions(allSteps, done = allSteps.drop(1).toSet()), vm.screen)
    }

    @Test
    fun `password problems are found before the phone is paired`() {
        val vm = viewModel()

        vm.submitConnect("12345678", "secret-1", "secret-2")
        assertEquals(UiMessage.PasswordsDiffer, vm.message)

        vm.submitConnect("12345678", "123", "123")
        assertEquals(UiMessage.PasswordTooShort, vm.message)

        assertEquals(0, pairing.pairCalls)
        assertFalse(password.set)
    }

    @Test
    fun `pairing errors keep the connect screen and explain`() {
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
            vm.submitConnect("12345678", "secret-1", "secret-1")
            assertEquals(KidsScreen.Connect(paired = false), vm.screen)
            assertEquals(message, vm.message)
            assertFalse(password.set)
        }
    }

    @Test
    fun `paired phone with an unsaved password does not ask for the code again`() {
        password.setResult = SetPasswordResult.TooShort
        val vm = viewModel()

        vm.submitConnect("12345678", "secret-1", "secret-1")

        assertEquals(KidsScreen.Connect(paired = true), vm.screen)
        assertEquals(UiMessage.PasswordTooShort, vm.message)

        password.setResult = SetPasswordResult.Saved
        vm.submitConnect("", "secret-1", "secret-1")
        assertEquals(1, pairing.pairCalls)
        assertTrue(vm.screen is KidsScreen.Permissions)
    }

    @Test
    fun `setup resumes where it stopped`() {
        store.values["setup_disclosure_accepted"] = "1".encodeToByteArray()
        pairing.paired = true

        assertEquals(KidsScreen.Connect(paired = true), viewModel().screen)
    }

    @Test
    fun `background work is an extra permission on aggressive firmware`() {
        environment.backgroundHint = true

        assertEquals(allSteps + SetupStep.BACKGROUND_WORK, connected().permissions.steps)
    }

    @Test
    fun `accessibility comes after the other permissions`() {
        environment.accessibility = false
        environment.usageAccess = false

        assertEquals(SetupStep.USAGE_ACCESS, setUpFully().permissions.current)
    }

    @Test
    fun `device owner skips the device admin and the vpn consent`() {
        protection.activeMode = ProtectionMode.DEVICE_OWNER

        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_OWNER), connected().screen)
    }

    @Test
    fun `one button walks through every permission`() {
        filter.permitted = false
        environment.notifications = false
        environment.location = false
        environment.locationForeground = false
        environment.usageAccess = false
        environment.accessibility = false
        val vm = connected().collectLaunches()

        vm.startPermissions()
        assertEquals(listOf(SystemLaunch(SetupStep.DEVICE_ADMIN)), launched)

        protection.activeMode = ProtectionMode.DEVICE_ADMIN
        vm.onSystemResult(SetupStep.DEVICE_ADMIN)
        assertEquals(SetupStep.WEB_FILTER, vm.state.value.consent)

        vm.acceptConsent()
        filter.permitted = true
        vm.onSystemResult(SetupStep.WEB_FILTER)
        environment.notifications = true
        vm.onSystemResult(SetupStep.NOTIFICATIONS)
        assertEquals(SetupStep.LOCATION, vm.state.value.consent)

        vm.acceptConsent()
        environment.locationForeground = true
        vm.onSystemResult(SetupStep.LOCATION)
        environment.location = true
        vm.onSystemResult(SetupStep.LOCATION)
        assertEquals(1, scheduler.scheduled)

        environment.usageAccess = true
        vm.onSystemResult(SetupStep.USAGE_ACCESS)
        assertEquals(1, usageScheduler.scheduled)
        assertEquals(SetupStep.ACCESSIBILITY, vm.state.value.consent)

        vm.acceptConsent()
        environment.accessibility = true
        vm.onSystemResult(SetupStep.ACCESSIBILITY)

        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_ADMIN), vm.screen)
        assertEquals(
            listOf(
                SetupStep.DEVICE_ADMIN,
                SetupStep.WEB_FILTER,
                SetupStep.NOTIFICATIONS,
                // Второй запрос — «Разрешить всегда» сразу после «При использовании».
                SetupStep.LOCATION,
                SetupStep.LOCATION,
                SetupStep.USAGE_ACCESS,
                SetupStep.ACCESSIBILITY
            ),
            launched.map { it.step }
        )
        assertNull(vm.message)
    }

    @Test
    fun `a refusal stops the chain and continue starts from the same permission`() {
        val vm = connected().collectLaunches()

        vm.startPermissions()
        vm.onSystemResult(SetupStep.DEVICE_ADMIN)

        assertEquals(UiMessage.AdminNotActivated, vm.message)
        assertEquals(SetupStep.DEVICE_ADMIN, vm.permissions.current)
        assertEquals(1, launched.size)

        vm.startPermissions()
        assertEquals(
            listOf(SetupStep.DEVICE_ADMIN, SetupStep.DEVICE_ADMIN),
            launched.map {
                it.step
            }
        )
    }

    @Test
    fun `closing a consent window stops the chain without opening anything`() {
        environment.accessibility = false
        val vm = setUpFully().collectLaunches()

        vm.startPermissions()
        vm.dismissConsent()

        assertNull(vm.state.value.consent)
        assertTrue(launched.isEmpty())
        assertEquals(SetupStep.ACCESSIBILITY, vm.permissions.current)
    }

    @Test
    fun `after denied notifications the next attempt opens the settings`() {
        environment.notifications = false
        val vm = setUpFully().collectLaunches()

        vm.startPermissions()
        vm.onSystemResult(SetupStep.NOTIFICATIONS)
        assertEquals(UiMessage.NotificationsDenied, vm.message)

        vm.startPermissions()
        assertEquals(
            listOf(
                SystemLaunch(SetupStep.NOTIFICATIONS),
                SystemLaunch(SetupStep.NOTIFICATIONS, viaSettings = true)
            ),
            launched
        )
    }

    @Test
    fun `denied location is not asked again in a loop`() {
        environment.location = false
        environment.locationForeground = false
        val vm = setUpFully().collectLaunches()

        vm.startPermissions()
        vm.acceptConsent()
        vm.onSystemResult(SetupStep.LOCATION)

        assertEquals(1, launched.size)
        assertEquals(UiMessage.StepNotDone, vm.message)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun `background work counts as done once the parent is back from the settings`() {
        environment.backgroundHint = true
        val vm = setUpFully()
        assertEquals(SetupStep.BACKGROUND_WORK, vm.permissions.current)

        vm.onSystemResult(SetupStep.BACKGROUND_WORK)

        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_ADMIN), vm.screen)
    }

    @Test
    fun `nothing to turn off before the device admin is on`() {
        assertFalse(connected().permissions.canTurnOff)
    }

    @Test
    fun `protection can be turned off from the permissions asking to re-enable something`() {
        // Ребёнок удалил VPN-профиль: защита работает, экран просит включить фильтр снова.
        filter.permitted = false
        val vm = setUpFully()
        assertEquals(setOf(SetupStep.WEB_FILTER), allSteps.toSet() - vm.permissions.done)
        assertTrue(vm.permissions.canTurnOff)

        vm.openDisableProtection()
        assertEquals(KidsScreen.DisableProtection, vm.screen)

        vm.back()
        assertEquals(SetupStep.WEB_FILTER, vm.permissions.current)
    }

    @Test
    fun `finishing setup with everything already granted still starts background work`() {
        // Сценарий с телефона: отвязка стёрла задачи, а при новой привязке пункты с выданными
        // разрешениями цепочка пропустила.
        val vm = setUpFully()
        vm.refresh()

        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_ADMIN), vm.screen)
        assertEquals(1, scheduler.scheduled)
        assertEquals(1, usageScheduler.scheduled)
    }

    @Test
    fun `usage access stays and schedules nothing until granted`() {
        environment.usageAccess = false
        val vm = setUpFully()

        vm.onSystemResult(SetupStep.USAGE_ACCESS)

        assertEquals(SetupStep.USAGE_ACCESS, vm.permissions.current)
        assertEquals(0, usageScheduler.scheduled)
    }

    @Test
    fun `revoked usage access brings back only that permission`() {
        val vm = setUpFully()
        assertEquals(KidsScreen.Home(ProtectionMode.DEVICE_ADMIN), vm.screen)

        environment.usageAccess = false
        vm.refresh()

        assertEquals(allSteps.toSet() - SetupStep.USAGE_ACCESS, vm.permissions.done)
        assertTrue(vm.permissions.canTurnOff)
    }

    @Test
    fun `auto return checks the permission itself`() {
        environment.usageAccess = false
        val vm = setUpFully()
        assertFalse(vm.isGranted(SetupStep.USAGE_ACCESS))

        environment.usageAccess = true
        assertTrue(vm.isGranted(SetupStep.USAGE_ACCESS))
        assertFalse(vm.isGranted(SetupStep.BACKGROUND_WORK))
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

    @Test
    fun `settings opened for a permission are trusted until the user comes back`() {
        val vm = setUpFully()

        vm.beforeSettingsScreen()
        assertTrue(trustedSettings.isOpen())

        vm.onSystemResult(SetupStep.USAGE_ACCESS)
        assertFalse(trustedSettings.isOpen())
    }

    @Test
    fun `coming back to the app closes the trusted window`() {
        val vm = setUpFully()
        vm.beforeSettingsScreen()

        vm.refresh()

        assertFalse(trustedSettings.isOpen())
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

        vm.submitConnect("12345678", "secret-1", "secret-1")

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
        var pairCalls = 0

        override suspend fun isPaired() = paired

        override suspend fun pair(code: String): PairingResult {
            pairCalls++
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
        var locationForeground = true
        var usageAccess = true
        var accessibility = true
        var backgroundHint = false

        override fun notificationsAllowed() = notifications

        override fun locationAllowed() = location

        override fun locationForegroundAllowed() = locationForeground

        override fun usageAccessAllowed() = usageAccess

        override fun accessibilityEnabled() = accessibility

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
