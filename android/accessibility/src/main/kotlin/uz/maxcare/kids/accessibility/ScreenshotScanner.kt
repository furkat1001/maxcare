package uz.maxcare.kids.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.maxcare.kids.core.ml.ImageClassifier
import uz.maxcare.kids.core.ml.ImageVerdict

/**
 * Снимает скриншот активного окна (API 30+), пропускает слишком частые и неизменные кадры,
 * прогоняет через [classifier]. При опасном вердикте вызывает [onUnsafe] на фоновом потоке —
 * переход на main делает вызывающая сторона. Кадр живёт только в памяти и сразу освобождается
 * (CLAUDE.md): не на диск, не в логи, не в аналитику.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal class ScreenshotScanner(
    private val service: AccessibilityService,
    private val classifier: ImageClassifier,
    private val scope: CoroutineScope,
    private val threshold: () -> Float,
    private val onUnsafe: (ImageVerdict) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var lastShotUptime = 0L
    private var lastHash = 0

    // Пропуск неизменного кадра применяем только пока контент безопасен; на опасном продолжаем
    // проверять, иначе «закрыл заглушку → тот же экран уже не блокируется».
    @Volatile
    private var lastVerdictSafe = true

    /**
     * Периодический захват: кадр раз в [intervalMs], пока [shouldScan] истинно (активен браузер/
     * мессенджер). Надёжнее привязки к событиям — браузеры шлют их нерегулярно. Неизменные кадры
     * всё равно отбрасываются по отпечатку. Возвращает [Job] для остановки.
     */
    fun startLoop(intervalMs: Long, shouldScan: () -> Boolean): Job = scope.launch {
        while (isActive) {
            delay(intervalMs)
            if (shouldScan()) maybeCapture(intervalMs)
        }
    }

    fun maybeCapture(intervalMs: Long) {
        val now = SystemClock.uptimeMillis()
        if (now - lastShotUptime < intervalMs) return
        lastShotUptime = now
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            executor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) = onShot(result)
                override fun onFailure(errorCode: Int) = Unit
            }
        )
    }

    private fun onShot(result: ScreenshotResult) {
        val buffer = result.hardwareBuffer
        val software = try {
            Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                ?.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            buffer.close()
        }
        if (software == null) return
        val fingerprint = fingerprint(software)
        // Пустой кадр браузер иногда отдаёт вместо контента — его нельзя засчитывать как
        // безопасный, пропускаем (таймер снимет следующий). Неизменный экран пропускаем только
        // пока он безопасен; опасный продолжаем блокировать.
        val skip = FrameQuality.isBlank(fingerprint) || (lastVerdictSafe && unchanged(fingerprint))
        if (skip) {
            software.recycle()
            return
        }
        scope.launch {
            val verdict = classifier.classify(software)
            software.recycle()
            // Порог зависит от строгости семьи; isSafe — нет модели.
            val blocked = !verdict.isSafe && verdict.confidence >= threshold()
            lastVerdictSafe = !blocked
            if (blocked) onUnsafe(verdict)
        }
    }

    /** Дешёвый отпечаток экрана 8×8 — для проверки на пустоту и неизменность. */
    private fun fingerprint(bitmap: Bitmap): IntArray {
        val thumb = bitmap.scale(HASH_SIZE, HASH_SIZE)
        val pixels = IntArray(HASH_SIZE * HASH_SIZE)
        thumb.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE)
        if (thumb !== bitmap) thumb.recycle()
        return pixels
    }

    /** Экран не изменился с прошлого кадра. Пустые кадры сюда не доходят, lastHash не засоряют. */
    private fun unchanged(fingerprint: IntArray): Boolean {
        val hash = fingerprint.contentHashCode()
        val same = hash == lastHash
        lastHash = hash
        return same
    }

    fun shutdown() = executor.shutdown()

    private companion object {
        const val HASH_SIZE = 8
    }
}
