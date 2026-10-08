package uz.maxcare.kids.protection

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceAdminActivationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun intentPointsToMaxCareAdminWithExplanation() {
        val intent = AndroidDeviceAdminActivation(context).activationIntent()

        assertEquals(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, intent.action)
        val admin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN)
        }
        assertEquals(ComponentName(context, MaxCareAdminReceiver::class.java), admin)
        assertTrue(
            intent.getStringExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION).orEmpty().isNotBlank()
        )
    }
}
