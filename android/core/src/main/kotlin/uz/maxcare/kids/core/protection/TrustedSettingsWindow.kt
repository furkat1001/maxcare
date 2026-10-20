package uz.maxcare.kids.core.protection

/**
 * Окно доверия для экранов Настроек, которые открыл сам MaxCare Kids: шаг мастера или
 * уведомление. Пока окно открыто, защита от удаления не закрывает экраны Настроек с названием
 * приложения, иначе мастер упирается в заглушку на шагах вроде «Доступ к истории использования».
 *
 * Окно короткое и закрывается при возврате в приложение: всё, что ребёнок откроет в Настройках
 * сам, по-прежнему перехватывается.
 */
interface TrustedSettingsWindow {
    /** Вызвать прямо перед запуском системного экрана. */
    fun open()

    /** Вызвать, когда пользователь вернулся в MaxCare Kids. */
    fun close()

    fun isOpen(): Boolean
}

/** @param now монотонное время в мс (`SystemClock.elapsedRealtime`): перевод часов не продлевает окно. */
class TimedTrustedSettingsWindow(private val now: () -> Long) : TrustedSettingsWindow {
    @Volatile
    private var openUntil = 0L

    override fun open() {
        openUntil = now() + WINDOW_MILLIS
    }

    override fun close() {
        openUntil = 0L
    }

    override fun isOpen(): Boolean = now() < openUntil

    companion object {
        /** Хватает, чтобы найти MaxCare Kids в списке и включить переключатель. */
        const val WINDOW_MILLIS = 90_000L
    }
}
