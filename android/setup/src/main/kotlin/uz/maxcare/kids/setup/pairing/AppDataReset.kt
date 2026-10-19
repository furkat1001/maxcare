package uz.maxcare.kids.setup.pairing

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Стереть все данные приложения, как «Очистить данные» в настройках. */
internal fun interface AppDataReset {
    fun reset()
}

/**
 * После отвязки на телефоне не должно остаться ничего от прежней семьи: токен, пароль, очереди
 * событий и маршрута, кэш ограничений и слов фильтра, а также то, что держат в памяти службы.
 * Системная очистка удаляет всё разом и завершает процесс — следующий запуск начнёт мастер с начала.
 */
internal class SystemAppDataReset @Inject constructor(
    @ApplicationContext private val context: Context
) : AppDataReset {
    override fun reset() {
        context.getSystemService(ActivityManager::class.java)?.clearApplicationUserData()
    }
}
