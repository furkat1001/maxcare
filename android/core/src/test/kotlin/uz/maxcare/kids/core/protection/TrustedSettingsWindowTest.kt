package uz.maxcare.kids.core.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedSettingsWindowTest {
    private var now = 1_000L
    private val window = TimedTrustedSettingsWindow { now }

    @Test
    fun `closed until MaxCare opens a settings screen`() {
        assertFalse(window.isOpen())
    }

    @Test
    fun `stays open for a short time only`() {
        window.open()
        now += TimedTrustedSettingsWindow.WINDOW_MILLIS - 1
        assertTrue(window.isOpen())

        now += 1
        assertFalse(window.isOpen())
    }

    @Test
    fun `returning to the app closes it at once`() {
        window.open()

        window.close()

        assertFalse(window.isOpen())
    }

    @Test
    fun `opening again restarts the window`() {
        window.open()
        now += TimedTrustedSettingsWindow.WINDOW_MILLIS - 10
        window.open()
        now += 20

        assertTrue(window.isOpen())
    }
}
