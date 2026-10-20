package uz.maxcare.kids.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.view.accessibility.AccessibilityEvent
import uz.maxcare.kids.core.events.EventType

/**
 * Перехват удаления приложения / снятия администратора из Настроек. Решение «блокировать ли» —
 * в чистом [UninstallGuard]; здесь — доступ к системе (активный админ, содержимое окна) и реакция:
 * увести на HOME и сообщить через [onBlocked].
 */
internal class UninstallInterceptor(
    private val service: AccessibilityService,
    private val openedByMaxCare: () -> Boolean,
    private val onBlocked: (EventType) -> Unit
) {

    /** @return true, если это была попытка удаления и мы её перехватили. */
    fun handle(event: AccessibilityEvent): Boolean {
        if (!isAttempt(event)) return false
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        onBlocked(EventType.UNINSTALL_ATTEMPT_BLOCKED)
        return true
    }

    private fun isAttempt(event: AccessibilityEvent): Boolean {
        // И STATE, и CONTENT changed: окно настроек наполняется не сразу, первое событие часто
        // приходит без текста (имя приложения ещё не отрисовано).
        val windowEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        val pkg = event.packageName?.toString()
        // Дешёвые проверки сначала; активный админ и обход дерева считаем только для нужного окна.
        if (!(windowEvent && pkg in UninstallGuard.SCREEN_PACKAGES)) return false
        return UninstallGuard.shouldBlock(
            windowEvent,
            pkg,
            adminActive(),
            appLabelOnScreen(),
            openedByMaxCare()
        )
    }

    private fun adminActive(): Boolean {
        val dpm = service.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.activeAdmins?.any { it.packageName == service.packageName } == true
    }

    private fun appLabelOnScreen(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return root.findAccessibilityNodeInfosByText(UninstallGuard.APP_LABEL).isNotEmpty()
    }
}
