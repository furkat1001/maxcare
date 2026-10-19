package uz.maxcare.kids.accessibility.limits

import android.content.pm.ApplicationInfo

/**
 * Группа приложения для правил на группу: `video`, `social`, `game`, `audio` или null.
 *
 * Основа — категория из манифеста приложения (`android:appCategory`). Многие приложения её не
 * указывают, поэтому популярные у детей приложения заданы явно. Мессенджеры намеренно без группы:
 * правило на «соцсети» не должно отрезать ребёнка от связи с родителями.
 */
internal object AppGroups {

    fun groupOf(packageName: String, category: Int): String? =
        if (packageName in KNOWN) KNOWN[packageName] else byCategory(category)

    private fun byCategory(category: Int): String? = when (category) {
        ApplicationInfo.CATEGORY_GAME -> GAME
        ApplicationInfo.CATEGORY_VIDEO -> VIDEO
        ApplicationInfo.CATEGORY_SOCIAL -> SOCIAL
        ApplicationInfo.CATEGORY_AUDIO -> AUDIO
        else -> null
    }

    private const val GAME = "game"
    private const val VIDEO = "video"
    private const val SOCIAL = "social"
    private const val AUDIO = "audio"

    private val KNOWN: Map<String, String?> = mapOf(
        "com.google.android.youtube" to VIDEO,
        "com.google.android.apps.youtube.kids" to VIDEO,
        "com.zhiliaoapp.musically" to VIDEO, // TikTok
        "com.ss.android.ugc.trill" to VIDEO, // TikTok
        "video.like" to VIDEO, // Likee
        "com.instagram.android" to SOCIAL,
        "com.facebook.katana" to SOCIAL,
        "com.snapchat.android" to SOCIAL,
        "com.vkontakte.android" to SOCIAL,
        "com.mojang.minecraftpe" to GAME,
        "com.roblox.client" to GAME,
        "com.tencent.ig" to GAME, // PUBG Mobile
        "com.axlebolt.standoff2" to GAME,
        "com.supercell.brawlstars" to GAME,
        "com.supercell.clashofclans" to GAME,
        "com.dts.freefireth" to GAME,
        "org.telegram.messenger" to null,
        "com.whatsapp" to null,
        "com.viber.voip" to null,
        "com.imo.android.imoim" to null
    )
}
