package uz.maxcare.kids.protection.service

import android.content.pm.ServiceInfo
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionForegroundTypeTest {
    private val special = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    private val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

    @Test
    fun `below Android 10 no typed foreground service`() {
        assertEquals(
            0,
            protectionForegroundType(Build.VERSION_CODES.O, hasLocationPermission = true)
        )
    }

    @Test
    fun `Android 10 to 13 adds location only when permitted`() {
        assertEquals(location, protectionForegroundType(Build.VERSION_CODES.TIRAMISU, true))
        assertEquals(0, protectionForegroundType(Build.VERSION_CODES.TIRAMISU, false))
    }

    @Test
    fun `Android 14 always declares special use for the notification`() {
        assertEquals(
            special,
            protectionForegroundType(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                hasLocationPermission = false
            )
        )
    }

    @Test
    fun `Android 14 adds location to special use only when permitted`() {
        assertEquals(
            special or location,
            protectionForegroundType(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                hasLocationPermission = true
            )
        )
    }
}
