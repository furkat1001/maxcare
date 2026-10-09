package uz.maxcare.backend.security

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RecoveryCodesTest {
    // Контрольные значения из RFC 6238, приложение B (SHA-256, 8 цифр, шаг 30 с).
    // Наш шаг — 5 минут, но алгоритм тот же: проверяем его на эталоне, передавая счётчик напрямую.
    private val rfcSeed = "12345678901234567890123456789012".encodeToByteArray()

    @Test
    fun `matches RFC 6238 SHA-256 test vectors`() {
        mapOf(
            59L to "46119246",
            1_111_111_109L to "68084774",
            1_111_111_111L to "67062674",
            1_234_567_890L to "91819424",
            2_000_000_000L to "90698825",
            20_000_000_000L to "77737706"
        ).forEach { (time, expected) ->
            assertEquals(expected, RecoveryCodes.code(rfcSeed, time / 30), "time $time")
        }
    }

    @Test
    fun `counter changes every five minutes`() {
        val start = Instant.parse("2026-10-09T10:00:00Z")

        assertEquals(
            RecoveryCodes.counterAt(start),
            RecoveryCodes.counterAt(start.plusSeconds(299))
        )
        assertEquals(
            RecoveryCodes.counterAt(start) + 1,
            RecoveryCodes.counterAt(start.plusSeconds(300))
        )
    }
}
