package uz.maxcare.kids.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.maxcare.kids.blocker.BlockerActivity
import uz.maxcare.kids.core.di.ApplicationScope
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.core.ml.ImageCategory
import uz.maxcare.kids.core.ml.ImageClassifier
import uz.maxcare.kids.core.ml.ImageVerdict

/**
 * Служба доступности фильтра контента.
 *
 * - Текст набора/поиска: опасный запрос — поле очищается (иначе заглушка), событие родителю.
 * - Защита от удаления: активный админ + экран Настроек с именем приложения → HOME + заглушка.
 * - Картинки: скриншот окон браузеров/мессенджеров → [ImageClassifier] → при опасном вердикте
 *   заглушка. Без файла модели классификатор возвращает SAFE — пайплайн вхолостую (PR 4a).
 *
 * Текст и кадры анализируются только в памяти: не на диск, не в логи, не в аналитику (CLAUDE.md).
 */
@AndroidEntryPoint
class ContentFilterService : AccessibilityService() {

    @Inject
    lateinit var events: EventRecorder

    @Inject
    lateinit var dictionary: DictionaryRepository

    @Inject
    lateinit var classifier: ImageClassifier

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    private val mainHandler = Handler(Looper.getMainLooper())

    private val uninstall = UninstallInterceptor(this) { trigger(it) }

    private var scanner: ScreenshotScanner? = null

    private var captureJob: Job? = null

    private var dictionaryJob: Job? = null

    @Volatile
    private var foregroundPackage: String? = null

    @Volatile
    private var lastTriggerUptime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        dictionaryJob = appScope.launch { keepDictionaryFresh() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val screenshotScanner = ScreenshotScanner(
                this,
                classifier,
                appScope,
                threshold = { dictionary.imageThreshold() }
            ) { verdict ->
                mainHandler.post { onUnsafeImage(verdict) }
            }
            scanner = screenshotScanner
            // Периодически снимаем кадр, пока на переднем плане браузер/мессенджер.
            captureJob = screenshotScanner.startLoop(SHOT_INTERVAL_MS) {
                foregroundPackage?.let(ScanTargets::matches) == true
            }
        }
    }

    /** Словарь: сразу из кэша (offline), затем периодически подтягиваем родительские слова. */
    private suspend fun keepDictionaryFresh() {
        dictionary.loadCached()
        while (currentCoroutineContext().isActive) {
            dictionary.refresh()
            delay(DICTIONARY_REFRESH_MS)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && pkg != packageName) foregroundPackage = pkg
        }
        when {
            uninstall.handle(event) -> Unit
            else -> filterText(event)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        dictionaryJob?.cancel()
        captureJob?.cancel()
        scanner?.shutdown()
        return super.onUnbind(intent)
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
        val dangerous = text.isNotBlank() && dictionary.current().matches(text)
        return if (dangerous) Hit(node, node?.isEditable == true) else null
    }

    private fun clearText(node: AccessibilityNodeInfo?) {
        node ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // --- Картинки ---

    private fun onUnsafeImage(verdict: ImageVerdict) {
        val type = if (verdict.category == ImageCategory.VIOLENCE) {
            EventType.IMAGE_BLOCKED_VIOLENCE
        } else {
            EventType.IMAGE_BLOCKED_NUDITY
        }
        trigger(type)
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

        // 1 с — у предела частоты takeScreenshot ОС (~1/сек): меньше «окно», в котором видео
        // проскакивает между кадрами. Лишние вызовы при троттлинге ОС просто отклоняются.
        const val SHOT_INTERVAL_MS = 1000L

        // Родительские слова подтягиваем раз в час: не нагружаем сеть, но без релиза.
        const val DICTIONARY_REFRESH_MS = 3_600_000L
    }
}
