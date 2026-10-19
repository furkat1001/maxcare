package uz.maxcare.kids.accessibility

/**
 * Где снимаем скриншоты для анализа картинок: браузеры и мессенджеры. Пока статично;
 * позже — из конфига/сервера (как словарь). Матч по подстроке пакета, чтобы ловить варианты.
 */
internal object ScanTargets {

    private val markers = setOf("chrome", "browser", "telegram", "whatsapp", "viber", "instagram")

    fun matches(packageName: String): Boolean = markers.any { packageName.contains(it) }
}
