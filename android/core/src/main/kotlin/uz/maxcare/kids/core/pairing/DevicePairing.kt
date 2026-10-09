package uz.maxcare.kids.core.pairing

/** Привязка телефона ребёнка к семье по одноразовому коду от родителя. */
interface DevicePairing {
    suspend fun isPaired(): Boolean

    /** @param code код из 8 цифр; пробелы и дефисы, которые родитель мог ввести для удобства, допустимы. */
    suspend fun pair(code: String): PairingResult
}

sealed interface PairingResult {
    data object Paired : PairingResult

    /** Телефон уже привязан; повторная привязка — только после отключения защиты. */
    data object AlreadyPaired : PairingResult

    /** Не 8 цифр — на сервер не отправлялось. */
    data object InvalidFormat : PairingResult

    /** Код неверный, истёк или уже использован. */
    data object InvalidCode : PairingResult

    /** Слишком много попыток с этого адреса, нужно подождать. */
    data object TooManyAttempts : PairingResult

    data object NoConnection : PairingResult

    /** Сертификат сервера не прошёл проверку — возможен перехват трафика. */
    data object InsecureConnection : PairingResult

    data object ServerError : PairingResult
}
