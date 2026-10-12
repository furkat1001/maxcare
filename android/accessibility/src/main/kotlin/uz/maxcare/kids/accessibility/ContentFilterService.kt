package uz.maxcare.kids.accessibility

import android.accessibilityservice.AccessibilityService
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
 * Текст набора/поиска: при опасном запросе редактируемое поле очищается, поверх показывается
 * заглушка, родителю уходит событие. Текст с экрана никуда не пишется — ни на диск, ни в логи,
 * ни в аналитику (CLAUDE.md).
 *
 * Пока обрабатывается только ввод (`TYPE_VIEW_TEXT_CHANGED`). Сканирование текста выдачи
 * (`WINDOW_CONTENT/STATE_CHANGED`) требует белых списков против ложных срабатываний и добавится
 * отдельно; картинки по скриншотам — PR 4; перехват экрана удаления — PR 3.
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
        val hit = dangerousInput(event) ?: return
        if (hit.isEditable) clearText(hit.node)
        trigger()
    }

    /** Опасный ввод в текущем событии: узел (если есть) и можно ли его очистить. */
    private fun dangerousInput(event: AccessibilityEvent?): Hit? {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return null
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

    /** Заглушка + событие, не чаще раза в [DEBOUNCE_MS]: набор и обновления экрана шлют события пачками. */
    private fun trigger() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTriggerUptime < DEBOUNCE_MS) return
        lastTriggerUptime = now
        showBlocker()
        appScope.launch { events.record(EventType.SEARCH_BLOCKED) }
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
