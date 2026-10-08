package uz.maxcare.kids.protection.password

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.protection.ChangePasswordResult
import uz.maxcare.kids.core.protection.PasswordCheck
import uz.maxcare.kids.core.protection.SetPasswordResult

class StoredParentPasswordTest {
    private val store = FakeSecureStore()
    private val clock = FakeDeviceClock()

    private fun TestScope.subject() = StoredParentPassword(
        store = store,
        hasher = PasswordHasher(iterations = 1_000),
        clock = clock,
        hashingDispatcher = StandardTestDispatcher(testScheduler)
    )

    private fun pw(text: String = PASSWORD) = text.toCharArray()

    private suspend fun StoredParentPassword.failTimes(times: Int) =
        List(times) { verify(pw("wrong-password")) }

    @Test
    fun `password is not set until saved`() = runTest {
        val password = subject()
        assertFalse(password.isSet())

        assertEquals(SetPasswordResult.Saved, password.setInitial(pw()))

        assertTrue(password.isSet())
        assertEquals(PasswordCheck.Correct, password.verify(pw()))
    }

    @Test
    fun `initial password cannot be overwritten`() = runTest {
        val password = subject()
        password.setInitial(pw())

        assertEquals(SetPasswordResult.AlreadySet, password.setInitial(pw("another-one")))
        assertEquals(PasswordCheck.Correct, password.verify(pw()))
    }

    @Test
    fun `short password is rejected`() = runTest {
        val password = subject()

        assertEquals(SetPasswordResult.TooShort, password.setInitial(pw("12345")))
        assertFalse(password.isSet())
    }

    @Test
    fun `five wrong attempts lock input for a minute`() = runTest {
        val password = subject()
        password.setInitial(pw())

        assertEquals(
            listOf(
                PasswordCheck.Wrong(4),
                PasswordCheck.Wrong(3),
                PasswordCheck.Wrong(2),
                PasswordCheck.Wrong(1),
                PasswordCheck.LockedOut(1.minutes)
            ),
            password.failTimes(5)
        )
    }

    @Test
    fun `correct password is not even checked during lockout`() = runTest {
        val password = subject()
        password.setInitial(pw())
        password.failTimes(5)

        clock.advance(59.seconds)
        assertEquals(PasswordCheck.LockedOut(1.seconds), password.verify(pw()))

        clock.advance(1.seconds)
        assertEquals(PasswordCheck.Correct, password.verify(pw()))
    }

    @Test
    fun `lockout escalates with each further failure`() = runTest {
        val password = subject()
        password.setInitial(pw())

        password.failTimes(5)
        clock.advance(1.minutes)
        assertEquals(listOf(PasswordCheck.LockedOut(5.minutes)), password.failTimes(1))
        clock.advance(5.minutes)
        assertEquals(listOf(PasswordCheck.LockedOut(15.minutes)), password.failTimes(1))
        clock.advance(15.minutes)
        assertEquals(listOf(PasswordCheck.LockedOut(60.minutes)), password.failTimes(1))
        clock.advance(60.minutes)
        assertEquals(listOf(PasswordCheck.LockedOut(60.minutes)), password.failTimes(1))
    }

    @Test
    fun `correct password resets the counter`() = runTest {
        val password = subject()
        password.setInitial(pw())
        password.failTimes(5)
        clock.advance(1.minutes)

        password.verify(pw())

        assertEquals(listOf(PasswordCheck.Wrong(4)), password.failTimes(1))
    }

    @Test
    fun `reboot restarts an unfinished lockout for its full length`() = runTest {
        val password = subject()
        password.setInitial(pw())
        password.failTimes(5)
        clock.advance(30.seconds)

        clock.reboot()

        assertEquals(1.minutes, password.lockoutRemaining())
        clock.advance(59.seconds)
        assertEquals(PasswordCheck.LockedOut(1.seconds), password.verify(pw()))
        clock.advance(1.seconds)
        assertEquals(PasswordCheck.Correct, password.verify(pw()))
    }

    @Test
    fun `no lockout means zero remaining`() = runTest {
        val password = subject()
        password.setInitial(pw())
        password.failTimes(4)

        assertEquals(Duration.ZERO, password.lockoutRemaining())
    }

    @Test
    fun `unreadable hash is unavailable, not unset`() = runTest {
        val password = subject()
        password.setInitial(pw())
        store.broken += "parent_password_hash"

        assertTrue(password.isSet())
        assertEquals(SetPasswordResult.AlreadySet, password.setInitial(pw("new-password")))
        assertEquals(PasswordCheck.Unavailable, password.verify(pw()))
    }

    @Test
    fun `verify without a password is unavailable`() = runTest {
        assertEquals(PasswordCheck.Unavailable, subject().verify(pw()))
    }

    @Test
    fun `unreadable lockout state means the strictest lockout`() = runTest {
        val password = subject()
        password.setInitial(pw())
        store.broken += "parent_password_lockout"

        assertEquals(PasswordCheck.LockedOut(60.minutes), password.verify(pw()))
    }

    @Test
    fun `garbled lockout state means the strictest lockout`() = runTest {
        val password = subject()
        password.setInitial(pw())
        store.values["parent_password_lockout"] = "0:0".encodeToByteArray()

        assertEquals(PasswordCheck.LockedOut(60.minutes), password.verify(pw()))
    }

    @Test
    fun `change requires the current password`() = runTest {
        val password = subject()
        password.setInitial(pw())

        assertEquals(
            ChangePasswordResult.Rejected(PasswordCheck.Wrong(4)),
            password.change(pw("wrong-password"), pw("new-password"))
        )
        assertEquals(ChangePasswordResult.Changed, password.change(pw(), pw("new-password")))
        assertEquals(PasswordCheck.Wrong(4), password.verify(pw()))
        assertEquals(PasswordCheck.Correct, password.verify(pw("new-password")))
    }

    @Test
    fun `too short new password does not use up an attempt`() = runTest {
        val password = subject()
        password.setInitial(pw())

        assertEquals(
            ChangePasswordResult.TooShort,
            password.change(pw("wrong-password"), pw("123"))
        )
        assertEquals(PasswordCheck.Wrong(4), password.verify(pw("wrong-password")))
    }

    @Test
    fun `password arrays are wiped after use`() = runTest {
        val password = subject()
        val initial = pw()
        val check = pw()
        val current = pw()
        val new = pw("new-password")

        password.setInitial(initial)
        password.verify(check)
        password.change(current, new)

        listOf(initial, check, current, new).forEach { array ->
            assertTrue(array.all { it == '\u0000' })
        }
    }

    private companion object {
        const val PASSWORD = "secret-password"
    }
}
