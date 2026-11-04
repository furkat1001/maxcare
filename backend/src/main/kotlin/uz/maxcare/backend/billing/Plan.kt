package uz.maxcare.backend.billing

import java.time.Duration
import java.time.Instant
import uz.maxcare.backend.parents.trialDaysLeft

/**
 * Состояние подписки семьи.
 * - [TRIAL] — идёт пробный период;
 * - [ACTIVE] — оплачено;
 * - [GRACE] — пробный период или оплата закончились, но ещё [GRACE_PERIOD] всё работает:
 *   карты из Узбекистана часто проходят не с первого раза;
 * - [EXPIRED] — отсрочка прошла: MaxCare останавливается до оплаты.
 */
enum class PlanStatus(val wireName: String) {
    TRIAL("trial"),
    ACTIVE("active"),
    GRACE("grace"),
    EXPIRED("expired")
}

data class Plan(
    val status: PlanStatus,
    val trialEndsAt: Instant,
    val trialDaysLeft: Int,
    val paidUntil: Instant?,
    /** Когда MaxCare остановится, если не оплатить. */
    val graceEndsAt: Instant
)

val GRACE_PERIOD: Duration = Duration.ofDays(3)

fun planAt(now: Instant, trialEndsAt: Instant, paidUntil: Instant?): Plan {
    val coveredUntil = if (paidUntil != null && paidUntil > trialEndsAt) paidUntil else trialEndsAt
    val graceEndsAt = coveredUntil.plus(GRACE_PERIOD)
    val daysLeft = trialDaysLeft(now, trialEndsAt)
    val status = when {
        paidUntil != null && paidUntil > now -> PlanStatus.ACTIVE
        daysLeft > 0 -> PlanStatus.TRIAL
        now < graceEndsAt -> PlanStatus.GRACE
        else -> PlanStatus.EXPIRED
    }
    return Plan(status, trialEndsAt, daysLeft, paidUntil, graceEndsAt)
}
