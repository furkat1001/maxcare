package uz.maxcare.kids.core.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Проверка на настоящем Android Keystore: эмулятор или телефон. */
@RunWith(AndroidJUnit4::class)
class KeystoreAeadTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun keyIsCreatedOnceAndReusedAfterRestart() {
        val plaintext = "device-token".encodeToByteArray()
        val ad = "token".encodeToByteArray()
        val ciphertext = createKeystoreAead(context).encrypt(plaintext, ad)

        // Новый экземпляр — как после перезапуска процесса: ключ должен загрузиться, а не создаться заново.
        val decrypted = createKeystoreAead(context).decrypt(ciphertext, ad)

        assertArrayEquals(plaintext, decrypted)
    }
}
