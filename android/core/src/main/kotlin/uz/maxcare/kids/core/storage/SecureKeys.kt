package uz.maxcare.kids.core.storage

/** Имена значений в [SecureStore], которые пишет один модуль, а читает другой. */
object SecureKeys {
    /** ID устройства на сервере; выдаётся при привязке. */
    const val DEVICE_ID = "device_id"

    /** Токен устройства для запросов к серверу; пишется последним — по нему определяется «привязан». */
    const val DEVICE_TOKEN = "device_token"

    /** Секрет для проверки кодов восстановления без сети (32 байта). */
    const val RECOVERY_SECRET = "recovery_secret"

    /** Кэш своих стоп-слов родителя (по строке на слово); подмешиваются к базовому словарю. */
    const val PARENT_FILTER_WORDS = "parent_filter_words"

    /** Кэш разрешённых слов родителя; убирают соответствующий корень из словаря. */
    const val PARENT_ALLOW_WORDS = "parent_allow_words"

    /** Уровень строгости фильтра: `standard` или `strict`. */
    const val FILTER_STRICTNESS = "filter_strictness"

    /** Кэш ограничений времени в приложениях (JSON с сервера); работают и без сети. */
    const val APP_LIMITS = "app_limits"

    /** Сколько времени уже потрачено сегодня, идущие перерывы и отсрочки — переживают перезапуск. */
    const val APP_LIMIT_STATE = "app_limit_state"
}
