package uz.maxcare.kids.core.limits

/** На что действует правило: одно приложение или группа по категории Android. */
enum class LimitKind(val wire: String) {
    APP("app"),
    GROUP("group");

    companion object {
        fun parse(value: String) = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Правило родителя. Формат совпадает с сервером: `docs/backend-api.md`, «Ограничение времени».
 * @param target пакет для [LimitKind.APP]; для [LimitKind.GROUP] — `video`, `social`, `game`, `audio`.
 * @param dailyLimitMin минут в день, после — закрыто до завтра; null — без дневного лимита.
 * @param sessionMin минут подряд, затем перерыв на [breakMin]; оба null — без цикла.
 */
data class AppLimitRule(
    val kind: LimitKind,
    val target: String,
    val dailyLimitMin: Int? = null,
    val sessionMin: Int? = null,
    val breakMin: Int? = null
) {
    /** Ключ правила: время копится по нему, а не по пакету — так группа считается вместе. */
    val key: String get() = "${kind.wire}:$target"
}

/** Набор ограничений телефона: правила и приложения, которые не блокируются никогда. */
data class AppLimitsConfig(
    val rules: List<AppLimitRule> = emptyList(),
    val alwaysAllowed: Set<String> = emptySet()
)

/** Сколько миллисекунд каждое приложение было на экране сегодня по системной истории. */
fun interface TodayUsage {
    /** Пакет → миллисекунды; пусто, если доступа к истории нет. */
    suspend fun foregroundToday(): Map<String, Long>
}

/** «Доиграть матч»: одна отсрочка для игры, когда сработал лимит. Вызывает экран-заглушка. */
interface TimeLimitExtension {
    /** Можно ли сейчас дать отсрочку для [packageName]. */
    fun canExtend(packageName: String): Boolean

    /** Дать отсрочку; false — уже нельзя (использована или это не игра). */
    fun extend(packageName: String): Boolean
}
