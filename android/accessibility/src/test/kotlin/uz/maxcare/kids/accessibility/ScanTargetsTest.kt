package uz.maxcare.kids.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTargetsTest {

    @Test
    fun `matches browsers and messengers by substring`() {
        assertTrue(ScanTargets.matches("com.android.chrome"))
        assertTrue(ScanTargets.matches("org.telegram.messenger"))
        assertTrue(ScanTargets.matches("com.whatsapp"))
    }

    @Test
    fun `ignores unrelated packages`() {
        assertFalse(ScanTargets.matches("com.android.settings"))
        assertFalse(ScanTargets.matches("uz.maxcare.kids"))
    }
}
