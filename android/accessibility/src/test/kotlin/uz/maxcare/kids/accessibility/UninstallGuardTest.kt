package uz.maxcare.kids.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallGuardTest {

    @Test
    fun `blocks settings screen with our app while admin is active`() {
        assertTrue(
            UninstallGuard.shouldBlock(
                isWindowEvent = true,
                packageName = "com.android.settings",
                adminActive = true,
                appLabelOnScreen = true
            )
        )
    }

    // Админ снят = защита отключена родителем → обычное удаление блокировать нельзя.
    @Test
    fun `does not block when admin is inactive`() {
        assertFalse(
            UninstallGuard.shouldBlock(
                isWindowEvent = true,
                packageName = "com.android.settings",
                adminActive = false,
                appLabelOnScreen = true
            )
        )
    }

    @Test
    fun `does not block when our app is not on screen`() {
        assertFalse(
            UninstallGuard.shouldBlock(
                isWindowEvent = true,
                packageName = "com.android.settings",
                adminActive = true,
                appLabelOnScreen = false
            )
        )
    }

    @Test
    fun `does not block unrelated packages`() {
        assertFalse(
            UninstallGuard.shouldBlock(
                isWindowEvent = true,
                packageName = "com.android.chrome",
                adminActive = true,
                appLabelOnScreen = true
            )
        )
    }

    @Test
    fun `does not block on non window-state events`() {
        assertFalse(
            UninstallGuard.shouldBlock(
                isWindowEvent = false,
                packageName = "com.android.settings",
                adminActive = true,
                appLabelOnScreen = true
            )
        )
    }
}
