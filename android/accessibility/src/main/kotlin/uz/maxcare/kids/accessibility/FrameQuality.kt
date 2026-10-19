package uz.maxcare.kids.accessibility

/**
 * Оценка пригодности кадра. `takeScreenshot` на веб-поверхности браузера иногда отдаёт
 * пустой/чёрный кадр вместо содержимого; его нельзя считать безопасным — такой кадр
 * пропускаем и ждём следующего (таймер снимает раз в ~1.5 с).
 *
 * Чистая логика по 8×8-отпечатку (тот же, что для детекции неизменности), без Android.
 */
internal object FrameQuality {

    // Разброс яркости ниже этого — кадр почти однотонный (чёрный/пустой). Реальный контент
    // (текст, картинки) даёт куда больший разброс. Порог консервативный: ложный пропуск кадра
    // безвреден — на следующем тике попробуем снова.
    private const val MIN_LUMA_RANGE = 12
    private const val MAX_LUMA = 255

    // Веса яркости (ITU-R BT.601), в сотых долях.
    private const val WEIGHT_R = 30
    private const val WEIGHT_G = 59
    private const val WEIGHT_B = 11
    private const val WEIGHT_SUM = 100

    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BYTE_MASK = 0xFF

    fun isBlank(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return true
        var min = MAX_LUMA
        var max = 0
        for (pixel in pixels) {
            val luma = luminance(pixel)
            if (luma < min) min = luma
            if (luma > max) max = luma
        }
        return (max - min) < MIN_LUMA_RANGE
    }

    private fun luminance(argb: Int): Int {
        val r = (argb shr RED_SHIFT) and BYTE_MASK
        val g = (argb shr GREEN_SHIFT) and BYTE_MASK
        val b = argb and BYTE_MASK
        return (r * WEIGHT_R + g * WEIGHT_G + b * WEIGHT_B) / WEIGHT_SUM
    }
}
