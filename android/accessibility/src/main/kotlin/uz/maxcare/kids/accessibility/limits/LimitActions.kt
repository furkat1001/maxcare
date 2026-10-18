package uz.maxcare.kids.accessibility.limits

import android.accessibilityservice.AccessibilityService
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.maxcare.kids.blocker.TimeLimitActivity
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType

/**
 * Что служба доступности делает по вердикту [AppLimitEnforcer]: предупреждает уведомлением или
 * закрывает приложение — на главный экран и экран «время вышло». Вызывать на главном потоке.
 */
internal class LimitActions(
    private val service: AccessibilityService,
    private val events: EventRecorder,
    private val enforcer: AppLimitEnforcer,
    private val scope: CoroutineScope
) {
    private val notifier = LimitNotifier(service)
    private var lastBlockUptime = 0L

    fun isScreenOn(): Boolean =
        service.getSystemService(PowerManager::class.java)?.isInteractive == true

    fun handle(pkg: String, verdict: LimitVerdict) {
        when (verdict) {
            is LimitVerdict.Warn -> notifier.warn(pkg, verdict.minutesLeft)
            is LimitVerdict.Blocked -> block(pkg, verdict)
            LimitVerdict.Free -> Unit
        }
    }

    private fun block(pkg: String, verdict: LimitVerdict.Blocked) {
        if (verdict.started) {
            scope.launch {
                enforcer.save()
                events.record(EventType.APP_LIMIT_REACHED, pkg)
            }
        }
        // Пока приложение уходит с экрана, тики продолжают приходить — заглушку не дублируем.
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockUptime < DEBOUNCE_MS) return
        lastBlockUptime = now
        // Сначала на главный экран — приложение уходит в фон, и «назад» к нему не вернёт.
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        val reason = when (verdict.reason) {
            LimitReason.DAILY -> TimeLimitActivity.REASON_DAILY
            LimitReason.BREAK -> TimeLimitActivity.REASON_BREAK
        }
        service.startActivity(TimeLimitActivity.intent(service, pkg, reason, verdict.untilMs))
    }

    private companion object {
        const val DEBOUNCE_MS = 1500L
    }
}
