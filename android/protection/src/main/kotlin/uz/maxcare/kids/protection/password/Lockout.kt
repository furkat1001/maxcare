package uz.maxcare.kids.protection.password

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Сколько ждать после очередной неверной попытки. */
internal object LockoutPolicy {
    /** Неверная попытка с этим номером включает первую блокировку. */
    const val FIRST_LOCKING_FAILURE = 5

    const val MAX_FAILURES = 8

    @Suppress("MagicNumber") // Таблица из плана спринта 1
    fun lockDuration(failures: Int): Duration = when {
        failures < FIRST_LOCKING_FAILURE -> Duration.ZERO
        failures == 5 -> 1.minutes
        failures == 6 -> 5.minutes
        failures == 7 -> 15.minutes
        else -> 60.minutes
    }
}

/**
 * @param lockedUntil момент окончания блокировки по [DeviceClock.elapsedRealtime] загрузки [bootCount];
 *   0 — блокировки нет.
 */
internal data class LockoutState(val failures: Int, val lockedUntil: Long, val bootCount: Int) {
    fun serialize() = "$failures:$lockedUntil:$bootCount"

    companion object {
        val NONE = LockoutState(failures = 0, lockedUntil = 0, bootCount = 0)

        private const val PARTS = 3

        fun parse(text: String): LockoutState? {
            val parts = text.split(':')
            if (parts.size != PARTS) return null
            val failures = parts[0].toIntOrNull()?.takeIf { it >= 0 }
            val lockedUntil = parts[1].toLongOrNull()
            val bootCount = parts[2].toIntOrNull()
            return if (failures != null && lockedUntil != null && bootCount != null) {
                LockoutState(failures, lockedUntil, bootCount)
            } else {
                null
            }
        }
    }
}

internal interface DeviceClock {
    /** Миллисекунды с момента включения телефона; не зависит от настроек даты и времени. */
    fun elapsedRealtime(): Long

    /** Номер загрузки телефона: меняется при каждой перезагрузке. */
    fun bootCount(): Int
}

internal class AndroidDeviceClock @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}
