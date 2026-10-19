package uz.maxcare.kids.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.FilterConfig
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

    private class FakeApi(private val config: ApiResult<FilterConfig>) : MaxCareApi {
        override suspend fun pairDevice(code: String, deviceName: String) = error("unused")
        override suspend fun uploadEvents(deviceToken: String, events: List<OutgoingEvent>) =
            error("unused")

        override suspend fun uploadLocations(
            deviceToken: String,
            locations: List<uz.maxcare.kids.core.network.OutgoingLocation>
        ) = error("unused")

        override suspend fun uploadAppUsage(
            deviceToken: String,
            days: List<uz.maxcare.kids.core.network.OutgoingDayUsage>
        ) = error("unused")

        override suspend fun fetchFilterConfig(deviceToken: String) = config

        override suspend fun fetchAppLimits(deviceToken: String) = error("unused")
    }

    private fun config(
        block: List<String> = emptyList(),
        allow: List<String> = emptyList(),
        strictness: String = "standard"
    ) = ApiResult.Ok(FilterConfig(block, allow, strictness))

    private fun repo(store: SecureStore, api: MaxCareApi) =
        DictionaryRepository(BaseDictionary(listOf("порн", "оружие")), store, api)

    private suspend fun withToken() =
        FakeStore().apply { writeString(SecureKeys.DEVICE_TOKEN, "tok") }

    @Test
    fun `base words match before any refresh`() {
        val repository = repo(FakeStore(), FakeApi(config()))
        assertTrue(repository.current().matches("порнуха"))
    }

    @Test
    fun `refresh adds block words and keeps base`() = runTest {
        val repository = repo(withToken(), FakeApi(config(block = listOf("казино"))))

        assertFalse(repository.current().matches("казино онлайн"))
        repository.refresh()

        assertTrue(repository.current().matches("казино онлайн"))
        assertTrue(repository.current().matches("порно"))
    }

    @Test
    fun `allow word disables a base stem`() = runTest {
        val repository = repo(withToken(), FakeApi(config(allow = listOf("оружие"))))

        assertTrue(repository.current().matches("оружие")) // до refresh базовый корень ловит
        repository.refresh()

        assertFalse(repository.current().matches("оружие"))
        assertTrue(repository.current().matches("порно")) // остальная база работает
    }

    @Test
    fun `strict strictness lowers the image threshold`() = runTest {
        val repository = repo(withToken(), FakeApi(config(strictness = "strict")))

        repository.refresh()

        assertTrue(repository.imageThreshold() < 0.5f)
    }

    @Test
    fun `standard strictness keeps the default threshold`() = runTest {
        val repository = repo(withToken(), FakeApi(config()))
        repository.refresh()
        assertEquals(0.5f, repository.imageThreshold(), 0.0001f)
    }

    @Test
    fun `refresh without a device token does nothing`() = runTest {
        val repository = repo(FakeStore(), FakeApi(config(block = listOf("казино"))))
        repository.refresh()
        assertFalse(repository.current().matches("казино"))
    }

    @Test
    fun `loadCached reads cached words`() = runTest {
        val store = FakeStore().apply { writeString(SecureKeys.PARENT_FILTER_WORDS, "ставки") }
        val repository = repo(store, FakeApi(ApiResult.NoConnection))

        repository.loadCached()

        assertTrue(repository.current().matches("ставки на спорт"))
    }

    @Test
    fun `refresh keeps base words when offline`() = runTest {
        val repository = repo(withToken(), FakeApi(ApiResult.NoConnection))
        repository.refresh()
        assertTrue(repository.current().matches("порно"))
    }
}
