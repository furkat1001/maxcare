package uz.maxcare.kids.accessibility

/**
 * Решение «это попытка удалить приложение / снять администратора» — чистая логика, без Android,
 * чтобы покрыть тестами.
 *
 * В обычном режиме Android карточка приложения в Настройках даёт «Деактивировать и удалить» одним
 * нажатием (см. docs/research.md). Классы экранов (SubSettings, AlertDialog) слишком общие, поэтому
 * опора на устойчивые признаки: окно Настроек/установщика + на экране есть имя нашего приложения
 * (бренд не локализуется) + наш администратор сейчас активен. Иначе легальное удаление после
 * отключения защиты родителем (администратор уже снят) блокировать нельзя.
 *
 * Исключение — экран, который только что открыл сам MaxCare Kids (шаг мастера, уведомление):
 * иначе мастер упирается в заглушку на «Доступе к истории использования» и подобных экранах.
 */
internal object UninstallGuard {

    /** Бренд ищем подстрокой в тексте экрана; не локализуется. */
    const val APP_LABEL = "MaxCare"

    val SCREEN_PACKAGES = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    fun shouldBlock(
        isWindowEvent: Boolean,
        packageName: String?,
        adminActive: Boolean,
        appLabelOnScreen: Boolean,
        openedByMaxCare: Boolean = false
    ): Boolean = isWindowEvent &&
        packageName in SCREEN_PACKAGES &&
        adminActive &&
        appLabelOnScreen &&
        !openedByMaxCare
}
