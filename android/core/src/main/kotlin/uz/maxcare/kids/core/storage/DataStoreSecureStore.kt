package uz.maxcare.kids.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import com.google.crypto.tink.Aead
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Каждое значение шифруется отдельно; имя ключа передаётся в AEAD как связанные данные,
 * поэтому зашифрованное значение нельзя переставить под другой ключ.
 *
 * [aead] ленивый: создание ключа в Android Keystore занимает заметное время,
 * и оно происходит при первом обращении на [ioDispatcher], а не при запуске приложения.
 */
internal class DataStoreSecureStore(
    private val dataStore: DataStore<Preferences>,
    private val aead: Lazy<Aead>,
    private val ioDispatcher: CoroutineDispatcher
) : SecureStore {

    override suspend fun read(key: String): ByteArray? = withContext(ioDispatcher) {
        val ciphertext = guard("read", key) { dataStore.data.first()[prefKey(key)] }
            ?: return@withContext null
        guard("decrypt", key) { aead.value.decrypt(ciphertext, associatedData(key)) }
    }

    override suspend fun write(key: String, value: ByteArray) = withContext(ioDispatcher) {
        val ciphertext = guard("encrypt", key) { aead.value.encrypt(value, associatedData(key)) }
        guard("write", key) { dataStore.edit { it[prefKey(key)] = ciphertext } }
        Unit
    }

    override suspend fun remove(key: String) = withContext(ioDispatcher) {
        guard("remove", key) { dataStore.edit { it.remove(prefKey(key)) } }
        Unit
    }

    private fun prefKey(key: String) = byteArrayPreferencesKey(key)

    private fun associatedData(key: String) = key.encodeToByteArray()

    // В сообщение попадает только имя ключа — никогда значение.
    private inline fun <T> guard(operation: String, key: String, block: () -> T): T = try {
        block()
    } catch (e: GeneralSecurityException) {
        throw SecureStoreException("Cannot $operation '$key'", e)
    } catch (e: IOException) {
        // Включает CorruptionException DataStore — повреждённый файл.
        throw SecureStoreException("Cannot $operation '$key'", e)
    }
}
