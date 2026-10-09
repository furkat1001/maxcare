package uz.maxcare.kids.protection.password

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryCodesTest {
    // RFC 6238, приложение B (SHA-256, 8 цифр). Те же значения проверяет сервер:
    // если оба теста зелёные, телефон и сервер считают коды одинаково.
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
            assertEquals("time $time", expected, RecoveryCodes.code(rfcSeed, time / 30))
        }
    }
}
