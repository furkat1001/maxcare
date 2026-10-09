package uz.maxcare.kids.protection.password

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.protection.PasswordCheck
import uz.maxcare.kids.core.protection.RecoveryResult
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.protection.FakeEventRecorder

class TotpPasswordRecoveryTest {
    private val store = FakeSecureStore()
    private val deviceClock = FakeDeviceClock()
    private val wallClock = MutableClock(Instant.parse("2026-10-09T10:02:00Z"))
    private val secret = ByteArray(32) { it.toByte() }
    private val events = FakeEventRecorder()

    private fun TestScope.password() = StoredParentPassword(
        store = store,
        hasher = PasswordHasher(iterations = 1_000),
        clock = deviceClock,
        hashingDispatcher = StandardTestDispatcher(testScheduler),
        events = events
    )

    private fun recovery(password: StoredParentPassword) =
        TotpPasswordRecovery(store, password, deviceClock, wallClock, events)

    private suspend fun TestScope.pairedWithPassword(): StoredParentPassword {
        store.values[SecureKeys.RECOVERY_SECRET] = secret
        return password().also { it.setInitial("forgotten-password".toCharArray()) }
    }

    private fun currentCode(offsetSteps: Long = 0) =
        RecoveryCodes.code(secret, RecoveryCodes.counterAt(wallClock.now) + offsetSteps)

    private fun newPassword() = "new-password".toCharArray()

    @Test
    fun `valid code sets a new password`() = runTest {
        val password = pairedWithPassword()

        assertEquals(RecoveryResult.Reset, recovery(password).reset(currentCode(), newPassword()))

        assertEquals(PasswordCheck.Correct, password.verify(newPassword()))
        assertEquals(PasswordCheck.Wrong(4), password.verify("forgotten-password".toCharArray()))
    }

    @Test
    fun `code from the previous interval is still accepted`() = runTest {
        val password = pairedWithPassword()

        assertEquals(RecoveryResult.Reset, recovery(password).reset(currentCode(-1), newPassword()))
    }

    @Test
    fun `older and future codes are rejected`() = runTest {
        val recovery = recovery(pairedWithPassword())

        assertEquals(RecoveryResult.InvalidCode(4), recovery.reset(currentCode(-2), newPassword()))
        assertEquals(RecoveryResult.InvalidCode(3), recovery.reset(currentCode(1), newPassword()))
    }

    @Test
    fun `a code works only once`() = runTest {
        val recovery = recovery(pairedWithPassword())
        val code = currentCode()
        recovery.reset(code, newPassword())

        assertEquals(
            RecoveryResult.InvalidCode(4),
            recovery.reset(code, "other-password".toCharArray())
        )
    }

    @Test
    fun `wrong codes lock recovery`() = runTest {
        val recovery = recovery(pairedWithPassword())

        val results = List(5) { recovery.reset("00000000", newPassword()) }

        assertEquals(RecoveryResult.LockedOut(1.minutes), results.last())
        assertEquals(
            RecoveryResult.LockedOut(1.minutes),
            recovery.reset(currentCode(), newPassword())
        )
    }

    @Test
    fun `recovery clears the password lockout`() = runTest {
        val password = pairedWithPassword()
        repeat(5) { password.verify("wrong-password".toCharArray()) }

        recovery(password).reset(currentCode(), newPassword())

        assertEquals(PasswordCheck.Correct, password.verify(newPassword()))
    }

    @Test
    fun `password and recovery have separate attempt counters`() = runTest {
        val password = pairedWithPassword()
        repeat(4) { password.verify("wrong-password".toCharArray()) }

        assertEquals(
            RecoveryResult.InvalidCode(4),
            recovery(password).reset("00000000", newPassword())
        )
    }

    @Test
    fun `bad format and short password do not use an attempt`() = runTest {
        val recovery = recovery(pairedWithPassword())

        assertEquals(RecoveryResult.InvalidFormat, recovery.reset("1234", newPassword()))
        assertEquals(RecoveryResult.TooShort, recovery.reset(currentCode(), "12345".toCharArray()))
        assertEquals(RecoveryResult.InvalidCode(4), recovery.reset("00000000", newPassword()))
    }

    @Test
    fun `spaces and dashes in the code are allowed`() = runTest {
        val password = pairedWithPassword()
        val spaced = currentCode().chunked(4).joinToString("-")

        assertEquals(RecoveryResult.Reset, recovery(password).reset(" $spaced ", newPassword()))
    }

    @Test
    fun `unpaired phone cannot recover`() = runTest {
        assertEquals(
            RecoveryResult.NotPaired,
            recovery(password()).reset(currentCode(), newPassword())
        )
    }

    @Test
    fun `unreadable secret is reported`() = runTest {
        val password = pairedWithPassword()
        store.broken += SecureKeys.RECOVERY_SECRET

        assertEquals(
            RecoveryResult.Unavailable,
            recovery(password).reset(currentCode(), newPassword())
        )
    }

    @Test
    fun `new password array is wiped`() = runTest {
        val array = newPassword()

        recovery(pairedWithPassword()).reset(currentCode(), array)

        assertTrue(array.all { it == '\u0000' })
    }

    @Test
    fun `password recovery is recorded`() = runTest {
        recovery(pairedWithPassword()).reset(currentCode(), newPassword())

        assertEquals(listOf(EventType.PASSWORD_RECOVERED), events.recorded)
    }

    @Test
    fun `wrong recovery code records nothing`() = runTest {
        recovery(pairedWithPassword()).reset("00000000", newPassword())

        assertTrue(events.recorded.isEmpty())
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }
}
