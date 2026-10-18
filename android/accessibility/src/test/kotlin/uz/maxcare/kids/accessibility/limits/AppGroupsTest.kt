package uz.maxcare.kids.accessibility.limits

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppGroupsTest {

    @Test
    fun `the manifest category decides for unknown apps`() {
        assertEquals("game", AppGroups.groupOf("com.example.game", ApplicationInfo.CATEGORY_GAME))
        assertEquals("video", AppGroups.groupOf("com.example.tv", ApplicationInfo.CATEGORY_VIDEO))
        assertNull(AppGroups.groupOf("com.example.notes", ApplicationInfo.CATEGORY_PRODUCTIVITY))
    }

    @Test
    fun `popular apps without a category are known`() {
        assertEquals("video", AppGroups.groupOf("com.google.android.youtube", -1))
        assertEquals("game", AppGroups.groupOf("com.mojang.minecraftpe", -1))
        assertEquals("social", AppGroups.groupOf("com.instagram.android", -1))
    }

    @Test
    fun `messengers are never in a group`() {
        assertNull(AppGroups.groupOf("org.telegram.messenger", ApplicationInfo.CATEGORY_SOCIAL))
    }
}
