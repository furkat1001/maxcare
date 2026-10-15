package uz.maxcare.kids.core.usage

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import uz.maxcare.kids.core.events.SyncResult

/**
 * Отправка времени в приложениях за последние дни; результат — тот же [SyncResult], что и у
 * событий (доставлено / повторить позже / телефон не привязан).
 */
interface AppUsageSync {
    suspend fun sync(): SyncResult
}

/** Запуск периодической отправки времени в приложениях. Реализация — в модуле `reports`. */
fun interface AppUsageScheduler {
    /** Поставить периодическую отправку (идемпотентно: повторный вызов не плодит задачи). */
    fun schedulePeriodic()
}

/**
 * Выдан ли «Доступ к истории использования» (`PACKAGE_USAGE_STATS`). Это не обычное разрешение,
 * а особый доступ: его включают вручную в настройках, поэтому проверяется через AppOps.
 */
fun Context.hasUsageAccess(): Boolean {
    val appOps = getSystemService(AppOpsManager::class.java) ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
