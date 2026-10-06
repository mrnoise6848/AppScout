package com.noise.appscout.data.remote.gemini

import com.noise.appscout.core.common.TimeProvider
import com.noise.appscout.domain.ai.AiApiKeyProvider
import com.noise.appscout.domain.ai.AiSummaryResult
import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.Release
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Phase 7 acceptance — provider behaviour against a real HTTP boundary:
 * valid output is parsed, anything else degrades to an explanatory failure.
 */
class GeminiAiSummaryProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: GeminiAiSummaryProvider

    private var apiKey: String? = "test-key"

    private val notes = """
        Fixed crash when opening settings.
        Improved startup performance.
    """.trimIndent()

    private fun release(body: String? = notes) = Release(
        id = "source#v1.2.3",
        sourceId = AppSource.githubId("owner", "repo"),
        tagName = "v1.2.3",
        normalizedVersion = "1.2.3",
        title = "Version 1.2.3",
        body = body,
        htmlUrl = "https://github.com/owner/repo/releases/tag/v1.2.3",
        publishedAt = 1_700_000_000_000L,
        isPrerelease = false,
        isDraft = false,
        assets = emptyList(),
        fetchedAt = 1_700_000_000_000L,
    )

    private val keyProvider = object : AiApiKeyProvider {
        override fun readApiKey(): String? = apiKey
    }

    private val timeProvider = object : TimeProvider {
        override fun nowMillis(): Long = 1_700_000_000_000L
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApi::class.java)
        provider = GeminiAiSummaryProvider(api, keyProvider, timeProvider)
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
    }

    private fun modelText(json: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(
            """{"candidates":[{"content":{"parts":[{"text":${jsonEscape(json)}}]}}]}""",
        )
        .build()

    private fun jsonEscape(text: String): String = kotlinx.serialization.json.Json.encodeToString(text)

    @Test
    fun `valid response is parsed into a structured summary`() = runTest {
        server.enqueue(
            modelText(
                """
                {"summary":"Fixes a settings crash and improves startup.","importance":"LOW",
                 "reasons":["Fixed crash when opening settings","Improved startup performance"],
                 "security_related":false,"breaking_change_possible":false}
                """.trimIndent(),
            ),
        )

        val result = provider.summarize(release(), "Firefox")

        assertTrue(result is AiSummaryResult.Success)
        val summary = (result as AiSummaryResult.Success).summary
        assertEquals("Fixes a settings crash and improves startup.", summary.summary)
        assertEquals(com.noise.appscout.domain.model.UpdateImportance.LOW, summary.importance)
        assertEquals(2, summary.reasons.size)
        assertFalse(summary.securityRelated)
        assertEquals("gemini-2.0-flash", summary.model)
        assertEquals(1_700_000_000_000L, summary.generatedAt)
    }

    @Test
    fun `supplied release notes are sent as the only evidence`() = runTest {
        server.enqueue(modelText("""{"summary":"x","importance":"LOW","reasons":[]}"""))

        provider.summarize(release(), "Firefox")

        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains("Fixed crash when opening settings."))
        assertTrue(body.contains("ONLY the release notes"))
        assertEquals("test-key", request.headers["x-goog-api-key"])
    }

    @Test
    fun `malformed response is reported as unavailable`() = runTest {
        server.enqueue(modelText("this is not json at all"))

        val result = provider.summarize(release(), "Firefox")

        assertEquals(AiSummaryResult.InvalidOutput, result)
    }

    @Test
    fun `response violating the output contract is rejected`() = runTest {
        server.enqueue(modelText("""{"importance":"LOW","reasons":[]}""")) // blank summary

        assertEquals(AiSummaryResult.InvalidOutput, provider.summarize(release(), "Firefox"))
    }

    @Test
    fun `unsupported security claims are removed, not trusted`() = runTest {
        server.enqueue(
            modelText(
                """
                {"summary":"Includes important security fixes.","importance":"HIGH",
                 "reasons":["Security patch included"],"security_related":true,
                 "breaking_change_possible":false}
                """.trimIndent(),
            ),
        )

        val result = provider.summarize(release(), "Firefox")

        assertTrue(result is AiSummaryResult.Success)
        val summary = (result as AiSummaryResult.Success).summary
        assertFalse(summary.securityRelated)
        assertTrue(summary.reasons.none { it.contains("Security", ignoreCase = true) })
    }

    @Test
    fun `release notes without a body cannot be summarized`() = runTest {
        assertEquals(AiSummaryResult.InvalidOutput, provider.summarize(release(body = null), "Firefox"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `missing api key short-circuits before any network call`() = runTest {
        apiKey = null

        assertEquals(AiSummaryResult.NoApiKey, provider.summarize(release(), "Firefox"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `rejected api key is distinguished from a network problem`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":{"status":"UNAUTHENTICATED"}}""").build())

        assertEquals(AiSummaryResult.InvalidApiKey, provider.summarize(release(), "Firefox"))
    }

    @Test
    fun `rate limit is reported as rate limited`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).body("""{"error":{"status":"RESOURCE_EXHAUSTED"}}""").build())

        assertEquals(AiSummaryResult.RateLimited, provider.summarize(release(), "Firefox"))
    }

    @Test
    fun `unreachable network degrades to a network error`() = runTest {
        server.close()

        assertEquals(AiSummaryResult.NetworkError, provider.summarize(release(), "Firefox"))
    }
}
