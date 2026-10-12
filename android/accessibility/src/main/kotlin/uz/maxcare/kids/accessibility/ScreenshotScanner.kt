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
    private val onUnsafe: (ImageVerdict) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var lastShotUptime = 0L
    private var lastHash = 0

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
        if (software == null || unchanged(software)) {
            software?.recycle()
            return
        }
        scope.launch {
            val verdict = classifier.classify(software)
            software.recycle()
            if (!verdict.isSafe) onUnsafe(verdict)
        }
    }

    /** Дешёвый отпечаток 8×8: пропускаем кадр, если экран не изменился. */
    private fun unchanged(bitmap: Bitmap): Boolean {
        val thumb = bitmap.scale(HASH_SIZE, HASH_SIZE)
        val pixels = IntArray(HASH_SIZE * HASH_SIZE)
        thumb.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE)
        if (thumb !== bitmap) thumb.recycle()
        val hash = pixels.contentHashCode()
        val same = hash == lastHash
        lastHash = hash
        return same
    }

    fun shutdown() = executor.shutdown()

    private companion object {
        const val HASH_SIZE = 8
    }
}
