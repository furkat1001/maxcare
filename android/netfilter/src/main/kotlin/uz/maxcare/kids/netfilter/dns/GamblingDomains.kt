package uz.maxcare.kids.netfilter.dns

/**
 * Азартные игры и ставки, популярные в Узбекистане и СНГ. Список составлен вручную (не из чужих
 * списков — нет вопроса лицензии) и блокирует домен вместе с поддоменами.
 *
 * Букмекеры постоянно меняют зеркала, поэтому список заведомо неполный; дальше — обновление
 * с сервера без обновления приложения (как модели ИИ в CLAUDE.md).
 */
internal object GamblingDomains {
    val LIST = setOf(
        "1xbet.com",
        "1xbet.uz",
        "melbet.com",
        "melbet.uz",
        "mostbet.com",
        "mostbet.uz",
        "betwinner.com",
        "pin-up.casino",
        "pinup.bet",
        "parimatch.com",
        "fonbet.ru",
        "winline.ru",
        "leon.ru",
        "olimp.bet",
        "ggbet.com",
        "bet365.com",
        "williamhill.com",
        "888casino.com",
        "pokerstars.com",
        "betandyou.com",
        "linebet.com",
        "22bet.com"
    )
}
