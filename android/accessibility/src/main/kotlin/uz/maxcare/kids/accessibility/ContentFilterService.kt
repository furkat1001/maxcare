package uz.maxcare.kids.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
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
 * В этом шаге — только текст в полях ввода (поиск): при опасном запросе поле очищается,
 * поверх показывается заглушка, родителю уходит событие. Текст с экрана никуда не пишется —
 * ни на диск, ни в логи, ни в аналитику (CLAUDE.md). Анализ картинок по скриншотам и
 * перехват экрана удаления добавятся отдельными шагами.
 */
@AndroidEntryPoint
class ContentFilterService : AccessibilityService() {

    @Inject
    lateinit var events: EventRecorder

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val node = dangerousEditableNode(event) ?: return
        clearText(node)
        showBlocker()
        appScope.launch { events.record(EventType.SEARCH_BLOCKED) }
    }

    /** Редактируемый узел (поле поиска/ввода) с опасным текстом, иначе null. */
    private fun dangerousEditableNode(event: AccessibilityEvent?): AccessibilityNodeInfo? {
        val node = event
            ?.takeIf { it.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED }
            ?.source
            ?.takeIf { it.isEditable }
        return node?.takeIf { StopWords.matches(it.text?.toString().orEmpty()) }
    }

    private fun clearText(node: AccessibilityNodeInfo) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun showBlocker() {
        val intent = Intent(this, BlockerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    override fun onInterrupt() = Unit
}
