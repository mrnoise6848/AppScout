package com.noise.appscout.data.remote.github

import com.noise.appscout.core.common.TimeProvider
import com.noise.appscout.domain.model.AppSource
import com.noise.appscout.domain.model.SourceErrorReason
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

/**
 * API tests against MockWebServer (spec §22).
 *
 * Every case must be deterministic: no live GitHub, no flakiness, no infinite retries.
 */
class GitHubReleaseDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: GitHubReleaseDataSource

    private val source = AppSource(
        id = AppSource.githubId("owner", "repo"),
        type = com.noise.appscout.domain.model.SourceType.GITHUB,
        owner = "owner",
        repository = "repo",
        url = AppSource.githubUrl("owner", "repo"),
    )

    private val timeProvider = object : TimeProvider {
        override fun nowMillis(): Long = 1_700_000_000_000L
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        dataSource = GitHubReleaseDataSource(
            api = retrofit.create(GitHubApi::class.java),
            timeProvider = timeProvider,
            io = Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
    }

    private fun releaseJson(
        tag: String = "v2.0.0",
        prerelease: Boolean = false,
        draft: Boolean = false,
        body: String? = "Bug fixes",
        publishedAt: String = "2026-01-01T00:00:00Z",
    ): String = """
        {
          "id": 1,
          "tag_name": "$tag",
          "name": "Release $tag",
          "body": ${body?.let { "\"${it}\"" } ?: "null"},
          "html_url": "https://github.com/owner/repo/releases/tag/$tag",
          "published_at": "$publishedAt",
          "draft": $draft,
          "prerelease": $prerelease,
          "assets": []
        }
    """.trimIndent()

    private fun enqueue(code: Int, body: String? = null, headers: List<String> = emptyList()) {
        val builder = MockResponse.Builder().code(code)
        if (body != null) builder.body(body)
        headers.forEach { builder.addHeader(it) }
        server.enqueue(builder.build())
    }

    // --- 200 -------------------------------------------------------------------------------

    @Test
    fun `200 latest release returns success and preserves etag`() = runTest {
        enqueue(200, releaseJson(), listOf("ETag: \"abc\""))

        val result = dataSource.fetchLatestRelease(source, cachedEtag = null, includePrerelease = false)

        assertTrue(result is ReleaseFetchResult.Success)
        result as ReleaseFetchResult.Success
        assertEquals("v2.0.0", result.release.tagName)
        assertEquals("2.0.0", result.release.normalizedVersion)
        assertEquals("\"abc\"", result.etag)
        assertEquals("Bug fixes", result.release.body)
        assertEquals(1_700_000_000_000L, result.release.fetchedAt)
    }

    @Test
    fun `changed release sends conditional etag header`() = runTest {
        enqueue(200, releaseJson(tag = "v2.1.0"))

        dataSource.fetchLatestRelease(source, cachedEtag = "\"old\"", includePrerelease = false)

        val request: RecordedRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("\"old\"", request.headers["If-None-Match"])
        assertEquals("/repos/owner/repo/releases/latest", request.url.encodedPath)
    }

    // --- 304 -------------------------------------------------------------------------------

    @Test
    fun `304 not modified keeps cache`() = runTest {
        enqueue(304)

        val result = dataSource.fetchLatestRelease(source, cachedEtag = "\"abc\"", includePrerelease = false)

        assertEquals(ReleaseFetchResult.NotModified, result)
    }

    // --- 404 -------------------------------------------------------------------------------

    @Test
    fun `404 with empty release list is NO_RELEASES`() = runTest {
        enqueue(404, """{"message":"Not Found"}""")
        enqueue(200, "[]")

        val result = dataSource.fetchLatestRelease(source, null, includePrerelease = false)

        assertTrue(result is ReleaseFetchResult.Failure)
        assertEquals(SourceErrorReason.NO_RELEASES, (result as ReleaseFetchResult.Failure).reason)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `404 on both endpoints is NOT_FOUND`() = runTest {
        enqueue(404, """{"message":"Not Found"}""")
        enqueue(404, """{"message":"Not Found"}""")

        val result = dataSource.fetchLatestRelease(source, null, includePrerelease = false)

        assertEquals(SourceErrorReason.NOT_FOUND, (result as ReleaseFetchResult.Failure).reason)
    }

    // --- 403 rate limit ---------------------------------------------------------------------

    @Test
    fun `403 rate limit fails fast without retries`() = runTest {
        enqueue(
            403,
            """{"message":"API rate limit exceeded"}""",
            listOf("X-RateLimit-Remaining: 0", "Retry-After: 60"),
        )

        val result = dataSource.fetchLatestRelease(source, null, includePrerelease = false)

        assertEquals(SourceErrorReason.RATE_LIMITED, (result as ReleaseFetchResult.Failure).reason)
        // no infinite retries: exactly one request
        assertEquals(1, server.requestCount)
    }

    // --- 500 -------------------------------------------------------------------------------

    @Test
    fun `500 server error is retried a bounded number of times`() = runTest {
        repeat(3) { enqueue(500, """{"message":"Server Error"}""") }

        val result = dataSource.fetchLatestRelease(source, null, includePrerelease = false)

        assertEquals(SourceErrorReason.SERVER_ERROR, (result as ReleaseFetchResult.Failure).reason)
        assertEquals(3, server.requestCount)
    }

    // --- malformed --------------------------------------------------------------------------

    @Test
    fun `malformed json is reported as malformed response`() = runTest {
        enqueue(200, "this is not json {")

        val result = dataSource.fetchLatestRelease(source, null, includePrerelease = false)

        assertEquals(SourceErrorReason.MALFORMED_RESPONSE, (result as ReleaseFetchResult.Failure).reason)
    }

    // --- empty list / no body ----------------------------------------------------------------

    @Test
    fun `release with no body still succeeds`() = runTest {
        enqueue(200, releaseJson(body = null))

        val result = dataSource.fetchLatestRelease(source, null, includePrerelease = false)

        assertTrue(result is ReleaseFetchResult.Success)
        assertNull((result as ReleaseFetchResult.Success).release.body)
    }

    @Test
    fun `empty release list fallback yields NO_RELEASES`() = runTest {
        enqueue(404)
        enqueue(200, "[]")

        val result = dataSource.fetchLatestRelease(source, null, includePrerelease = false)

        assertEquals(SourceErrorReason.NO_RELEASES, (result as ReleaseFetchResult.Failure).reason)
    }

    // --- prerelease filtering ---------------------------------------------------------------

    @Test
    fun `prerelease is skipped when disabled`() = runTest {
        enqueue(200, releaseJson(tag = "v2.0.0-beta.1", prerelease = true))
        enqueue(
            200,
            """
            [
              {"id":1,"tag_name":"v2.0.0-beta.1","prerelease":true,"draft":false,"published_at":"2026-02-01T00:00:00Z","assets":[]},
              {"id":2,"tag_name":"v1.9.0","prerelease":false,"draft":false,"published_at":"2026-01-01T00:00:00Z","assets":[]}
            ]
            """.trimIndent(),
        )

        val result = dataSource.fetchLatestRelease(source, null, includePrerelease = false)

        assertTrue(result is ReleaseFetchResult.Success)
        assertEquals("v1.9.0", (result as ReleaseFetchResult.Success).release.tagName)
    }

    @Test
    fun `prerelease is used when enabled`() = runTest {
        enqueue(200, releaseJson(tag = "v2.0.0-beta.1", prerelease = true))
        enqueue(
            200,
            """
            [
              {"id":1,"tag_name":"v2.0.0-beta.1","prerelease":true,"draft":false,"published_at":"2026-02-01T00:00:00Z","assets":[]},
              {"id":2,"tag_name":"v1.9.0","prerelease":false,"draft":false,"published_at":"2026-01-01T00:00:00Z","assets":[]}
            ]
            """.trimIndent(),
        )

        val result = dataSource.fetchLatestRelease(source, null, includePrerelease = true)

        assertTrue(result is ReleaseFetchResult.Success)
        assertEquals("v2.0.0-beta.1", (result as ReleaseFetchResult.Success).release.tagName)
    }

    @Test
    fun `draft releases are never eligible`() = runTest {
        enqueue(200, releaseJson(tag = "v9.9.9", draft = true))
        enqueue(200, """[{"id":5,"tag_name":"v9.9.9","draft":true,"prerelease":false,"assets":[]}]""")

        val result = dataSource.fetchLatestRelease(source, null, includePrerelease = true)

        assertEquals(SourceErrorReason.NO_RELEASES, (result as ReleaseFetchResult.Failure).reason)
    }
}
