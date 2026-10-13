package uz.maxcare.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import uz.maxcare.backend.filterwords.FilterWordRequest
import uz.maxcare.backend.filterwords.FilterWordsResponse

class FilterWordsApiTest : ApiTestBase() {

    private suspend fun HttpClient.addWord(word: String, token: String) =
        postJson("/v1/filter-words", FilterWordRequest(word), token)

    private suspend fun HttpClient.words(token: String) =
        getJson("/v1/filter-words", token).body<FilterWordsResponse>().words

    private suspend fun HttpClient.deleteWord(word: String, token: String) =
        delete("/v1/filter-words") {
            bearerAuth(token)
            parameter("word", word)
        }

    @Test
    fun `parent adds a word and sees it`() = apiTest { client ->
        val parent = client.registerAndLogin()

        assertEquals(HttpStatusCode.Created, client.addWord("казино", parent).status)

        assertEquals(listOf("казино"), client.words(parent))
    }

    @Test
    fun `child device sees the family words`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)
        client.addWord("ставки", parent)

        val words = client.getJson("/v1/device/filter-words", device.deviceToken)
            .body<FilterWordsResponse>().words

        assertEquals(listOf("ставки"), words)
    }

    @Test
    fun `words are isolated per family`() = apiTest { client ->
        val parentA = client.registerAndLogin()
        val parentB = client.registerAndLogin()
        client.addWord("казино", parentA)

        assertEquals(emptyList(), client.words(parentB))
    }

    @Test
    fun `word is trimmed, lowercased and deduplicated`() = apiTest { client ->
        val parent = client.registerAndLogin()

        client.addWord("  КАЗИНО  ", parent)
        client.addWord("казино", parent)

        assertEquals(listOf("казино"), client.words(parent))
    }

    @Test
    fun `blank word is rejected`() = apiTest { client ->
        val parent = client.registerAndLogin()

        assertEquals(HttpStatusCode.BadRequest, client.addWord("   ", parent).status)
    }

    @Test
    fun `delete removes a word`() = apiTest { client ->
        val parent = client.registerAndLogin()
        client.addWord("казино", parent)

        assertEquals(HttpStatusCode.NoContent, client.deleteWord("казино", parent).status)

        assertEquals(emptyList(), client.words(parent))
    }
}
