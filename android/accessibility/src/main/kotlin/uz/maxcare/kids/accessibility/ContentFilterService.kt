package uz.maxcare.kids.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.maxcare.kids.blocker.BlockerActivity
import uz.maxcare.kids.core.di.ApplicationScope
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType

/**
 * Служба доступности фильтра контента.
 *
 * - Текст набора/поиска: при опасном запросе редактируемое поле очищается, иначе показывается
 *   заглушка; родителю уходит событие. Текст с экрана никуда не пишется (CLAUDE.md).
 * - Защита от удаления: если наш администратор активен и в Настройках/установщике открыт экран с
 *   именем приложения («Деактивировать и удалить»), уводим на HOME и показываем заглушку.
 *
 * Картинки по скриншотам — PR 4; сканирование текста выдачи — отдельно (нужны белые списки).
 */
@AndroidEntryPoint
class ContentFilterService : AccessibilityService() {

    @Inject
    lateinit var events: EventRecorder

    @Inject
    lateinit var forbidden: ForbiddenWords

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    @Volatile
    private var lastTriggerUptime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when {
            isUninstallAttempt(event) -> blockUninstall()
            else -> filterText(event)
        }
    }

    // --- Защита от удаления ---

    private fun isUninstallAttempt(event: AccessibilityEvent): Boolean {
        // И STATE, и CONTENT changed: окно настроек наполняется не сразу, первое событие часто
        // приходит без текста (имя приложения ещё не отрисовано).
        val windowEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        val pkg = event.packageName?.toString()
        // Дешёвые проверки сначала; активный админ и обход дерева считаем только для нужного окна.
        if (!(windowEvent && pkg in UninstallGuard.SCREEN_PACKAGES)) return false
        return UninstallGuard.shouldBlock(windowEvent, pkg, adminActive(), appLabelOnScreen())
    }

    private fun adminActive(): Boolean {
        val dpm = getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.activeAdmins?.any { it.packageName == packageName } == true
    }

    private fun appLabelOnScreen(): Boolean {
        val root = rootInActiveWindow ?: return false
        return root.findAccessibilityNodeInfosByText(UninstallGuard.APP_LABEL).isNotEmpty()
    }

    private fun blockUninstall() {
        // HOME всегда (это и есть защита), заглушка и событие — через общий дебаунс.
        performGlobalAction(GLOBAL_ACTION_HOME)
        trigger(EventType.UNINSTALL_ATTEMPT_BLOCKED)
    }

    // --- Фильтр текста ---

    private fun filterText(event: AccessibilityEvent) {
        val hit = dangerousInput(event) ?: return
        if (hit.isEditable) clearText(hit.node)
        trigger(EventType.SEARCH_BLOCKED)
    }

    /** Опасный ввод в текущем событии: узел (если есть) и можно ли его очистить. */
    private fun dangerousInput(event: AccessibilityEvent): Hit? {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return null
        val node = event.source
        // Поле (как поиск Google) отдаёт текст через узел; где узел пуст (как omnibox Chrome) —
        // пробуем текст самого события.
        val text = node?.text?.toString()?.ifBlank { null } ?: event.text.joinToString(" ")
        val dangerous = text.isNotBlank() && forbidden.matches(text)
        return if (dangerous) Hit(node, node?.isEditable == true) else null
    }

    private fun clearText(node: AccessibilityNodeInfo?) {
        node ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // --- Общее ---

    /** Заглушка + событие, не чаще раза в [DEBOUNCE_MS]: экраны шлют события пачками. */
    private fun trigger(type: EventType) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTriggerUptime < DEBOUNCE_MS) return
        lastTriggerUptime = now
        showBlocker()
        appScope.launch { events.record(type) }
    }

    private fun showBlocker() {
        val intent = Intent(this, BlockerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    override fun onInterrupt() = Unit

    private class Hit(val node: AccessibilityNodeInfo?, val isEditable: Boolean)

    private companion object {
        const val DEBOUNCE_MS = 1500L
    }
}
