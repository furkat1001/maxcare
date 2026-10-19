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

/**
 * Результат по кадру. [category] — наиболее вероятный опасный класс, [confidence] — его
 * вероятность (без порога: порог применяет вызывающая сторона, он зависит от строгости).
 * [category] = null — кадр не анализировался (нет модели).
 */
data class ImageVerdict(val category: ImageCategory?, val confidence: Float) {
    /** Кадр не анализировался (нет модели). Порог блокировки — на вызывающей стороне. */
    val isSafe: Boolean get() = category == null

    companion object {
        /** Кадр не анализировался. */
        val SAFE = ImageVerdict(category = null, confidence = 0f)
    }
}

enum class ImageCategory { NUDITY, VIOLENCE }
