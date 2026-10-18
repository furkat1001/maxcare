package uz.maxcare.kids.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameQualityTest {

    @Test
    fun `solid black frame is blank`() {
        assertTrue(FrameQuality.isBlank(IntArray(64) { 0xFF000000.toInt() }))
    }

    @Test
    fun `uniform gray frame is blank`() {
        assertTrue(FrameQuality.isBlank(IntArray(64) { 0xFF808080.toInt() }))
    }

    @Test
    fun `frame with contrast is not blank`() {
        val pixels = IntArray(64) { if (it % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        assertFalse(FrameQuality.isBlank(pixels))
    }

    @Test
    fun `empty array is blank`() {
        assertTrue(FrameQuality.isBlank(IntArray(0)))
    }
}
