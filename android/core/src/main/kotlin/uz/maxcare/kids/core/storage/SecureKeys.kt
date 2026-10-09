package uz.maxcare.kids.core.storage

/** Имена значений в [SecureStore], которые пишет один модуль, а читает другой. */
object SecureKeys {
    /** ID устройства на сервере; выдаётся при привязке. */
    const val DEVICE_ID = "device_id"

    /** Токен устройства для запросов к серверу; пишется последним — по нему определяется «привязан». */
    const val DEVICE_TOKEN = "device_token"

    /** Секрет для проверки кодов восстановления без сети (32 байта). */
    const val RECOVERY_SECRET = "recovery_secret"
}
