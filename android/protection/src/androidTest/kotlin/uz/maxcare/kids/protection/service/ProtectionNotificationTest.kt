package uz.maxcare.kids.protection.service

import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectionNotificationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun notificationIsOngoingAndSilent() {
        ProtectionNotification.ensureChannel(context)

        val notification = ProtectionNotification.build(context)

        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(ProtectionNotification.CHANNEL_ID, notification.channelId)
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(ProtectionNotification.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }
}
