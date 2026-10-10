package uz.maxcare.kids.core.network

/** Запросы телефона ребёнка к серверу MaxCare. Описание API — `docs/backend-api.md`. */
interface MaxCareApi {
    /** Обменять одноразовый код от родителя на токен устройства и секрет восстановления. */
    suspend fun pairDevice(code: String, deviceName: String): ApiResult<PairedDevice>

    /**
     * Отправить события (до 100) с токеном устройства. Пустой список — отметка «на связи».
     * Повторная отправка тех же событий безопасна: сервер узнаёт их по [OutgoingEvent.id].
     */
    suspend fun uploadEvents(deviceToken: String, events: List<OutgoingEvent>): ApiResult<Unit>
}

/** @param occurredAt время по часам телефона в формате ISO-8601. */
class OutgoingEvent(
    val id: String,
    val type: String,
    val occurredAt: String,
    val detail: String? = null
)

class PairedDevice(val deviceId: String, val deviceToken: String, val recoverySecret: ByteArray)

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** Сервер ответил ошибкой; [code] — машинный код из тела ответа, например `invalid_code`. */
    data class HttpError(val status: Int, val code: String?) : ApiResult<Nothing>

    /** Нет сети или сервер не отвечает. */
    data object NoConnection : ApiResult<Nothing>

    /**
     * TLS не прошёл: сертификат сервера не совпал с привязкой или не проверился.
     * Возможен перехват трафика — это не то же самое, что «нет сети».
     */
    data object InsecureConnection : ApiResult<Nothing>

    /** Ответ пришёл, но его не удалось разобрать. */
    data object BadResponse : ApiResult<Nothing>
}
