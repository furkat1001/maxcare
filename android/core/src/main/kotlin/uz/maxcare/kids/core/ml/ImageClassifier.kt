package uz.maxcare.kids.core.ml

import android.graphics.Bitmap

/**
 * Классификатор кадра экрана: нагота и насилие. Реализация — модуль `ml` (LiteRT),
 * вызывающая сторона — служба доступности в `accessibility`, которая снимает скриншот.
 *
 * Кадр анализируется только в памяти: не писать на диск, в логи, в аналитику (CLAUDE.md).
 */
interface ImageClassifier {
    /**
     * Классифицировать один кадр.
     *
     * Тяжёлая операция — вызывать с фонового диспетчера; бюджет ≤ 100 мс на кадр на
     * телефоне среднего класса (CLAUDE.md). Реализация не удерживает [bitmap] после
     * возврата — освобождение кадра на вызывающей стороне.
     */
    suspend fun classify(bitmap: Bitmap): ImageVerdict
}

/** Что ИИ нашёл на кадре. [category] = null — ничего опасного не найдено. */
data class ImageVerdict(val category: ImageCategory?, val confidence: Float) {
    val isSafe: Boolean get() = category == null

    companion object {
        /** Кадр безопасен. */
        val SAFE = ImageVerdict(category = null, confidence = 0f)
    }
}

enum class ImageCategory { NUDITY, VIOLENCE }
