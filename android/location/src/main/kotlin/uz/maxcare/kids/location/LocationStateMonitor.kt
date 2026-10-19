package uz.maxcare.kids.location

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

/** Можно ли сейчас снять позицию в фоне. */
internal enum class LocationAccess {
    ON,

    /** Переключатель геолокации выключен; разрешение есть. */
    SWITCHED_OFF,

    /** Нет разрешения на геолокацию или на работу с ней в фоне. */
    DENIED
}

internal fun interface LocationAccessProbe {
    fun current(): LocationAccess
}

/** Уведомление ребёнку «геолокация выключена — включи». */
internal interface LocationNotice {
    fun show(access: LocationAccess)
    fun hide()
}

/**
 * Последнее увиденное состояние. [reported] — родитель уже получил событие о том, что геолокация
 * перестала работать; только тогда возврат в [LocationAccess.ON] даёт [EventType.LOCATION_ON].
 */
internal data class LocationState(val access: LocationAccess, val reported: Boolean) {
    fun encode() = "${access.name}:${if (reported) 1 else 0}"

    companion object {
        fun decode(value: String?): LocationState? {
            val parts = value?.split(':')?.takeIf { it.size == 2 } ?: return null
            return LocationAccess.entries.firstOrNull { it.name == parts[0] }
                ?.let { LocationState(it, parts[1] == "1") }
        }
    }
}

internal class LocationTransition(val state: LocationState, val event: EventType?)

/**
 * Событие пишется, только когда **работавшая** геолокация перестала работать. Первая проверка
 * лишь запоминает состояние: служба запускается ещё до шага мастера с разрешением, и «нет доступа»
 * в этот момент — не действие ребёнка (как у уведомлений, см. NotificationStateMonitor).
 */
internal fun locationTransition(before: LocationState?, now: LocationAccess): LocationTransition {
    val event = when {
        before == null || before.access == now -> null

        now == LocationAccess.ON -> EventType.LOCATION_ON.takeIf { before.reported }

        // Переход «выключено ↔ нет доступа» сообщаем, только если о поломке уже знают:
        // иначе это всё ещё настройка, а не обход.
        before.access == LocationAccess.ON || before.reported -> offEvent(now)

        else -> null
    }
    val reported = when {
        now == LocationAccess.ON -> false
        before?.access == now -> before.reported
        else -> event != null
    }
    return LocationTransition(LocationState(now, reported), event)
}

private fun offEvent(access: LocationAccess) =
    if (access == LocationAccess.DENIED) EventType.LOCATION_DENIED else EventType.LOCATION_OFF

/**
 * Следит, работает ли геолокация: пишет событие родителю и показывает ребёнку уведомление,
 * по нажатию на которое открываются нужные настройки. Включить геолокацию само приложение
 * не может — это доступно только в режиме Device Owner (`protection/`).
 */
@Singleton
internal class LocationStateMonitor @Inject constructor(
    private val store: SecureStore,
    private val events: EventRecorder,
    private val probe: LocationAccessProbe,
    private val notice: LocationNotice
) {
    // Проверка идёт из службы и из WorkManager; без мьютекса обе прочитали бы старое состояние
    // и записали событие дважды.
    private val mutex = Mutex()

    suspend fun check() = mutex.withLock {
        val now = probe.current()
        val state = try {
            val before = LocationState.decode(store.readString(STATE_KEY))
            val transition = locationTransition(before, now)
            transition.event?.let { events.record(it) }
            if (transition.state != before) store.writeString(STATE_KEY, transition.state.encode())
            transition.state
        } catch (ignored: SecureStoreException) {
            // Событие попробуем при следующей проверке; уведомление ребёнку покажем и так.
            LocationState(now, reported = false)
        }
        updateNotice(state)
    }

    // «Выключено» показываем всегда: разрешение уже выдано, значит мастер пройден.
    // «Нет доступа» — только после события: до шага мастера доступа нет законно.
    private fun updateNotice(state: LocationState) {
        val visible = state.access == LocationAccess.SWITCHED_OFF ||
            (state.access == LocationAccess.DENIED && state.reported)
        if (visible) notice.show(state.access) else notice.hide()
    }

    private companion object {
        const val STATE_KEY = "location_last_state"
    }
}
