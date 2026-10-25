package uz.maxcare.kids.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.location.LocationFix

class LocationAccuracyTest {
    private fun fix(accuracy: Double?) = LocationFix(41.0, 69.0, accuracy)

    @Test
    fun `a precise fix needs no gps`() {
        assertFalse(needsGps(fix(30.0)))
        assertFalse(needsGps(fix(100.0)))
    }

    @Test
    fun `a rough or missing fix asks gps`() {
        assertTrue(needsGps(fix(200.0)))
        assertTrue(needsGps(null))
    }

    @Test
    fun `the more accurate of two fixes wins`() {
        val wifi = fix(200.0)
        val gps = fix(5.0)

        assertSame(gps, moreAccurate(wifi, gps))
        assertSame(gps, moreAccurate(gps, fix(300.0)))
    }

    @Test
    fun `a missing gps fix keeps the quick one`() {
        val wifi = fix(200.0)

        assertSame(wifi, moreAccurate(wifi, null))
        assertEquals(5.0, moreAccurate(null, fix(5.0))?.accuracyMeters)
        assertNull(moreAccurate(null, null))
    }

    @Test
    fun `a fix with unknown accuracy loses to one with known accuracy`() {
        val known = fix(150.0)

        assertSame(known, moreAccurate(fix(null), known))
    }
}
