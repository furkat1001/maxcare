package uz.maxcare.kids.reports

import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.maxcare.kids.core.di.ApplicationScope
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventSync
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.storage.SecureStoreException

/**
 * Записывает событие в очередь и сразу пытается отправить — не дожидаясь периодической отправки:
 * например, событие «защита отключена» должно успеть уйти до того, как служба остановится.
 */
@Singleton
internal class QueueingEventRecorder @Inject constructor(
    private val queue: SecureEventQueue,
    private val sync: EventSync,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope
) : EventRecorder {

    override suspend fun record(type: EventType) {
        val event = QueuedEvent(
            id = UUID.randomUUID().toString(),
            type = type.wireName,
            occurredAt = clock.instant().toString()
        )
        try {
            queue.add(event)
        } catch (ignored: SecureStoreException) {
            // Запись события не должна ломать то, что его вызвало (приёмник, проверку пароля).
            return
        }
        appScope.launch { sync.sync() }
    }
}
