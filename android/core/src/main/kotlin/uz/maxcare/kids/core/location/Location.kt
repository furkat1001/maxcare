package uz.maxcare.kids.core.location

import uz.maxcare.kids.core.events.SyncResult

/**
 * Снимок позиции телефона. Время не здесь: его ставит сборщик по общим часам приложения,
 * как и для событий. Только координаты и точность — никакого содержимого (CLAUDE.md).
 */
class LocationFix(val latitude: Double, val longitude: Double, val accuracyMeters: Double?)

/** Источник текущей позиции телефона. Реализация — в модуле `location`. */
interface LocationProvider {
    /** @return текущий фикс или null, если его не получить (нет разрешения, геолокация выключена). */
    suspend fun current(): LocationFix?
}

/** Снять текущую позицию и положить в очередь на отправку. */
interface LocationCollector {
    /** @return true, если фикс получен и поставлен в очередь. */
    suspend fun collect(): Boolean
}

/**
 * Отправка накопленных позиций; результат — тот же [SyncResult], что и у событий
 * (доставлено / повторить позже / телефон не привязан).
 */
interface LocationSync {
    suspend fun sync(): SyncResult
}
