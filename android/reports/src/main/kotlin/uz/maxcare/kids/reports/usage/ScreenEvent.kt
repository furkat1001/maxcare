package uz.maxcare.kids.reports.usage

import java.time.Instant

/** Событие истории использования, уже без Android-типов. */
internal sealed interface ScreenEvent {
    val at: Instant

    /** Окно приложения вышло на экран. [activity] различает окна одного приложения. */
    data class Resumed(override val at: Instant, val packageName: String, val activity: String) :
        ScreenEvent

    /** Окно ушло с экрана. */
    data class Paused(override val at: Instant, val packageName: String, val activity: String) :
        ScreenEvent

    /** Экран погас или телефон выключается: на экране больше ничего нет. */
    data class ScreenOff(override val at: Instant) : ScreenEvent
}
