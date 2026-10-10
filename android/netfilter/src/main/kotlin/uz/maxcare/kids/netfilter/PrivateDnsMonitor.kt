package uz.maxcare.kids.netfilter

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

/**
 * Записывает [EventType.PRIVATE_DNS_ON] при включении «Частного DNS» с адресом сервера —
 * один раз на каждое включение. В отличие от уведомлений, пишется и при первой проверке:
 * «Частный DNS», включённый ещё до установки MaxCare Kids, обходит фильтр точно так же.
 */
@Singleton
internal class PrivateDnsMonitor @Inject constructor(
    private val store: SecureStore,
    private val events: EventRecorder,
    private val probe: PrivateDnsProbe
) {
    private val mutex = Mutex()

    suspend fun check() = mutex.withLock {
        try {
            val now = if (probe.isStrictPrivateDnsOn()) ON else OFF
            val before = store.readString(STATE_KEY)
            if (now == ON && before != ON) events.record(EventType.PRIVATE_DNS_ON)
            if (before != now) store.writeString(STATE_KEY, now)
        } catch (ignored: SecureStoreException) {
            // Проверим при следующем вызове.
        }
    }

    private companion object {
        const val STATE_KEY = "private_dns_last_state"
        const val ON = "on"
        const val OFF = "off"
    }
}
