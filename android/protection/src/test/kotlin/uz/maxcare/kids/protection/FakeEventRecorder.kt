package uz.maxcare.kids.protection

import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType

internal class FakeEventRecorder : EventRecorder {
    val recorded = mutableListOf<EventType>()

    override suspend fun record(type: EventType) {
        recorded += type
    }
}
