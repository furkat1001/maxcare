package uz.maxcare.kids.reports.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import uz.maxcare.kids.core.usage.hasUsageAccess

/** Системная история использования; интерфейс — для тестов без Android. */
internal interface UsageHistory {
    /** Выдан ли «Доступ к истории использования». */
    fun hasAccess(): Boolean

    /** События «окно на экране / ушло / экран погас» за период, по возрастанию времени. */
    fun events(from: Instant, to: Instant): List<ScreenEvent>

    /**
     * Приложения со значком на рабочем столе: пакет → название, как его видит ребёнок.
     * Без лаунчера (он на экране между приложениями) и без самого MaxCare Kids.
     */
    fun launchableApps(): Map<String, String>
}

internal class AndroidUsageHistory @Inject constructor(
    @ApplicationContext private val context: Context
) : UsageHistory {

    override fun hasAccess(): Boolean = context.hasUsageAccess()

    override fun events(from: Instant, to: Instant): List<ScreenEvent> {
        val events = context.getSystemService(UsageStatsManager::class.java)
            ?.queryEvents(from.toEpochMilli(), to.toEpochMilli())
            ?: return emptyList()
        val result = mutableListOf<ScreenEvent>()
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            event.toScreenEvent()?.let(result::add)
        }
        return result
    }

    private fun UsageEvents.Event.toScreenEvent(): ScreenEvent? {
        val at = Instant.ofEpochMilli(timeStamp)
        val activity = className.orEmpty()
        return when (eventType) {
            ACTIVITY_RESUMED -> ScreenEvent.Resumed(at, packageName, activity)
            ACTIVITY_PAUSED -> ScreenEvent.Paused(at, packageName, activity)
            SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN -> ScreenEvent.ScreenOff(at)
            else -> null
        }
    }

    override fun launchableApps(): Map<String, String> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        // Видимость пакетов (Android 11+) даёт <queries> в манифесте, без QUERY_ALL_PACKAGES.
        val homePackages = pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && it.packageName !in homePackages }
            .associate { it.packageName to it.applicationInfo.loadLabel(pm).toString() }
    }

    private companion object {
        // Числа, а не константы UsageEvents.Event: имена ACTIVITY_* появились в API 29, а значения
        // те же с API 21 (MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND). minSdk — 26.
        const val ACTIVITY_RESUMED = 1
        const val ACTIVITY_PAUSED = 2

        // API 28+. На более старых телефонах сессия закроется следующим окном или концом периода.
        const val SCREEN_NON_INTERACTIVE = 16

        // API 29+.
        const val DEVICE_SHUTDOWN = 26
    }
}
