package uz.maxcare.kids.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputTextTest {

    @Test
    fun `password fields are never read, even with visible characters`() {
        assertNull(inputText(true, false, null, listOf("synthetic-secret")))
        assertNull(inputText(false, true, "synthetic-secret", emptyList()))
        assertNull(inputText(true, true, "synthetic-secret", listOf("synthetic-secret")))
    }

    @Test
    fun `search field text comes from the node`() {
        assertEquals("query", inputText(false, false, "query", listOf("other")))
    }

    @Test
    fun `empty node falls back to the event text`() {
        assertEquals("omni box", inputText(false, false, " ", listOf("omni", "box")))
        assertEquals("omni", inputText(false, false, null, listOf("omni")))
    }

    @Test
    fun `nothing typed means nothing to check`() {
        assertNull(inputText(false, false, null, emptyList()))
        assertNull(inputText(false, false, "", listOf(" ")))
    }
}
