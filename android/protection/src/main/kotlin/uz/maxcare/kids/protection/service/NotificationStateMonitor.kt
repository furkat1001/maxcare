package uz.maxcare.kids.protection.service

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.storage.writeString

/** Видно ли постоянное уведомление: разрешены уведомления приложения и не выключен канал. */
internal fun interface NotificationVisibility {
    fun isVisible(): Boolean
}

internal class AndroidNotificationVisibility @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationVisibility {
    override fun isVisible(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        val channel = manager.getNotificationChannelCompat(ProtectionNotification.CHANNEL_ID)
        return manager.areNotificationsEnabled() &&
            channel?.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }
}

/**
 * Записывает [EventType.NOTIFICATIONS_OFF] один раз при выключении уведомлений, а не при каждой
 * проверке: иначе раз в 15 минут в журнал родителя падало бы одно и то же событие.
 */
@Singleton
internal class NotificationStateMonitor @Inject constructor(
    private val store: SecureStore,
    private val events: EventRecorder,
    private val notifications: NotificationVisibility
) {
    suspend fun check() {
        try {
            val visible = notifications.isVisible()
            val reported = store.readString(REPORTED_KEY) == FLAG_SET
            when {
                !visible && !reported -> {
                    events.record(EventType.NOTIFICATIONS_OFF)
                    store.writeString(REPORTED_KEY, FLAG_SET)
                }

                visible && reported -> store.remove(REPORTED_KEY)
            }
        } catch (ignored: SecureStoreException) {
            // Проверим снова через 15 минут; служба из-за этого падать не должна.
        }
    }

    private companion object {
        const val REPORTED_KEY = "notifications_off_reported"
        const val FLAG_SET = "1"
    }
}
