package uz.maxcare.kids.protection.service

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Записывает [EventType.NOTIFICATIONS_OFF] только при переходе «были видны → выключены».
 * Первая проверка лишь запоминает состояние: служба может запуститься ещё до шага мастера
 * «Разрешить уведомления», и выключенные уведомления в этот момент — не действие ребёнка.
 * Повторные проверки того же состояния ничего не пишут: иначе событие падало бы раз в 15 минут.
 */
@Singleton
internal class NotificationStateMonitor @Inject constructor(
    private val store: SecureStore,
    private val events: EventRecorder,
    private val notifications: NotificationVisibility
) {
    // Проверки идут из службы и из applyPolicies почти одновременно: без мьютекса обе читали
    // «видимы» до записи «скрыты» и записывали событие дважды (найдено на Samsung).
    private val mutex = Mutex()

    suspend fun check() = mutex.withLock {
        try {
            val now = if (notifications.isVisible()) VISIBLE else HIDDEN
            val before = store.readString(STATE_KEY)
            if (before == VISIBLE && now == HIDDEN) events.record(EventType.NOTIFICATIONS_OFF)
            if (before != now) store.writeString(STATE_KEY, now)
        } catch (ignored: SecureStoreException) {
            // Проверим снова через 15 минут; служба из-за этого падать не должна.
        }
    }

    private companion object {
        const val STATE_KEY = "notifications_last_state"
        const val VISIBLE = "visible"
        const val HIDDEN = "hidden"
    }
}
