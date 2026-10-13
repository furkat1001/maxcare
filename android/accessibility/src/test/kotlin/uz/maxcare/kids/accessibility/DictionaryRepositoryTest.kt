package uz.maxcare.kids.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.network.OutgoingEvent
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.writeString

class DictionaryRepositoryTest {

    private class FakeStore : SecureStore {
        private val data = mutableMapOf<String, ByteArray>()
        override suspend fun read(key: String): ByteArray? = data[key]
        override suspend fun write(key: String, value: ByteArray) {
            data[key] = value
        }

        override suspend fun remove(key: String) {
            data.remove(key)
        }
    }

    private class FakeApi(private val words: ApiResult<List<String>>) : MaxCareApi {
        override suspend fun pairDevice(code: String, deviceName: String) = error("unused")
        override suspend fun uploadEvents(deviceToken: String, events: List<OutgoingEvent>) =
            error("unused")

        override suspend fun fetchFilterWords(deviceToken: String) = words
    }

    private fun repo(store: SecureStore, api: MaxCareApi) =
        DictionaryRepository(BaseDictionary(listOf("порн")), store, api)

    @Test
    fun `base words match before any refresh`() {
        val repository = repo(FakeStore(), FakeApi(ApiResult.Ok(emptyList())))
        assertTrue(repository.current().matches("порнуха"))
    }

    @Test
    fun `refresh adds parent words and keeps base`() = runTest {
        val store = FakeStore().apply { writeString(SecureKeys.DEVICE_TOKEN, "tok") }
        val repository = repo(store, FakeApi(ApiResult.Ok(listOf("казино"))))

        assertFalse(repository.current().matches("казино онлайн"))
        repository.refresh()

        assertTrue(repository.current().matches("казино онлайн"))
        assertTrue(repository.current().matches("порно"))
    }

    @Test
    fun `refresh without a device token does nothing`() = runTest {
        val repository = repo(FakeStore(), FakeApi(ApiResult.Ok(listOf("казино"))))
        repository.refresh()
        assertFalse(repository.current().matches("казино"))
    }

    @Test
    fun `loadCached reads parent words from the store`() = runTest {
        val store = FakeStore().apply { writeString(SecureKeys.PARENT_FILTER_WORDS, "ставки") }
        val repository = repo(store, FakeApi(ApiResult.NoConnection))

        repository.loadCached()

        assertTrue(repository.current().matches("ставки на спорт"))
    }

    @Test
    fun `refresh keeps base words when offline`() = runTest {
        val store = FakeStore().apply { writeString(SecureKeys.DEVICE_TOKEN, "tok") }
        val repository = repo(store, FakeApi(ApiResult.NoConnection))

        repository.refresh()

        assertTrue(repository.current().matches("порно"))
    }
}
