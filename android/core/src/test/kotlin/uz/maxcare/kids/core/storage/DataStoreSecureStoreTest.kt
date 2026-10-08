package uz.maxcare.kids.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class DataStoreSecureStoreTest {
    private val fileSystem = FakeFileSystem()
    private val path = "/secure.preferences_pb".toPath()

    private lateinit var aead: Aead
    private var job = Job()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SecureStore

    @Before
    fun setUp() {
        AeadConfig.register()
        aead = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        open()
    }

    @After
    fun tearDown() = runBlocking { job.cancelAndJoin() }

    private fun open() {
        job = Job()
        dataStore = PreferenceDataStoreFactory.create(
            storage = OkioStorage(fileSystem, PreferencesSerializer, producePath = { path }),
            scope = CoroutineScope(Dispatchers.IO + job)
        )
        store = DataStoreSecureStore(dataStore, lazyOf(aead), Dispatchers.IO)
    }

    @Test
    fun `written value is read back`() = runTest {
        store.write("token", byteArrayOf(1, 2, 3))

        assertArrayEquals(byteArrayOf(1, 2, 3), store.read("token"))
    }

    @Test
    fun `missing key reads as null`() = runTest {
        assertNull(store.read("token"))
    }

    @Test
    fun `write overwrites and remove deletes`() = runTest {
        store.writeString("token", "first")
        store.writeString("token", "second")
        assertEquals("second", store.readString("token"))

        store.remove("token")
        assertNull(store.read("token"))
    }

    @Test
    fun `non-latin text survives round trip`() = runTest {
        val text = "Ҳимоя ҳали созланмаган / Himoya o‘chirilgan / Защита"
        store.writeString("note", text)

        assertEquals(text, store.readString("note"))
    }

    @Test
    fun `value is not stored in plain text`() = runTest {
        val secret = "parent-password-hash".encodeToByteArray()
        store.write("hash", secret)

        val raw = dataStore.data.first()[byteArrayPreferencesKey("hash")]!!
        assertFalse(raw.decodeToString().contains("parent-password-hash"))
    }

    @Test
    fun `value moved under another key is rejected`() = runTest {
        store.writeString("token", "device-token")
        val raw = dataStore.data.first()[byteArrayPreferencesKey("token")]!!
        dataStore.edit { it[byteArrayPreferencesKey("password_hash")] = raw }

        assertThrows(SecureStoreException::class.java) {
            runBlocking { store.read("password_hash") }
        }
    }

    @Test
    fun `tampered value is rejected, not read as missing`() = runTest {
        store.writeString("hash", "value")
        val raw = dataStore.data.first()[byteArrayPreferencesKey("hash")]!!
        raw[raw.lastIndex] = (raw.last().toInt() xor 1).toByte()
        dataStore.edit { it[byteArrayPreferencesKey("hash")] = raw }

        assertThrows(SecureStoreException::class.java) {
            runBlocking { store.read("hash") }
        }
    }

    @Test
    fun `corrupted file is reported, not read as empty`() = runTest {
        job.cancelAndJoin()
        fileSystem.write(path) { writeUtf8("not a protobuf") }
        open()

        assertThrows(SecureStoreException::class.java) {
            runBlocking { store.read("hash") }
        }
    }

    @Test
    fun `values survive reopening the store`() = runTest {
        store.writeString("token", "device-token")
        job.cancelAndJoin()

        open()

        assertEquals("device-token", store.readString("token"))
    }

    @Test
    fun `different key cannot decrypt`() = runTest {
        store.writeString("token", "device-token")
        val otherAead = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        val otherStore = DataStoreSecureStore(dataStore, lazyOf(otherAead), Dispatchers.IO)

        assertThrows(SecureStoreException::class.java) {
            runBlocking { otherStore.read("token") }
        }
    }
}
