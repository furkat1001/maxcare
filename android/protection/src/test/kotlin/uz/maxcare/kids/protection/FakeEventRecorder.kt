package uz.maxcare.kids.protection

import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType

internal class FakeEventRecorder : EventRecorder {
    val recorded = mutableListOf<EventType>()
    val details = mutableListOf<String?>()

    override suspend fun record(type: EventType, detail: String?) {
        recorded += type
        details += detail
    }
}
