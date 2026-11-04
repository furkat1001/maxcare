package uz.maxcare.kids.setup.ui

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Открывает системное окно для пункта экрана «Разрешения»; требует Activity. */
internal fun interface SystemActions {
    fun launch(request: SystemLaunch)
}

/**
 * Возвращает в MaxCare Kids, как только разрешение включено в Настройках: родителю не нужно
 * искать кнопку «Назад». Проверка — раз в [POLL_MILLIS], пока Настройки открыты, не дольше
 * [WATCH_MILLIS].
 *
 * С Android 10 приложение не может открыть свой экран из фона. Для специальных возможностей
 * Android это разрешает, как только служба включена. Для остального — только если Настройки
 * открылись в задаче MaxCare Kids; если нет, вызов просто не сработает и родитель вернётся сам.
 */
internal class SettingsReturn(private val activity: KidsActivity) {
    private var watch: Job? = null

    fun watch(isGranted: () -> Boolean) {
        cancel()
        watch = activity.lifecycleScope.launch {
            repeat((WATCH_MILLIS / POLL_MILLIS).toInt()) {
                delay(POLL_MILLIS)
                if (isGranted()) {
                    bringBack()
                    return@launch
                }
            }
        }
    }

    /** При возврате в приложение: следить больше не за чем. */
    fun cancel() {
        watch?.cancel()
        watch = null
    }

    // CLEAR_TOP закрывает Настройки над экраном приложения, и результат приходит как обычно.
    private fun bringBack() {
        activity.startActivity(
            Intent(activity, KidsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private companion object {
        const val POLL_MILLIS = 700L
        const val WATCH_MILLIS = 180_000L
    }
}
