package com.noise.appscout.data.remote.github

import com.noise.appscout.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Acceptance test A — GitHub URL parsing. */
class GitHubSourceUrlParserTest {

    private val parser = GitHubSourceUrlParser()

    @Test
    fun `plain repository url parses owner and repository`() {
        val parsed = parser.parse("https://github.com/owner/repo")
        assertNotNull(parsed)
        assertEquals("owner", parsed!!.owner)
        assertEquals("repo", parsed.repository)
        assertEquals(SourceType.GITHUB, parsed.type)
        assertEquals("https://github.com/owner/repo", parsed.url)
    }

    @Test
    fun `trailing slash is accepted`() {
        val parsed = parser.parse("https://github.com/owner/repo/")
        assertNotNull(parsed)
        assertEquals("owner", parsed!!.owner)
        assertEquals("repo", parsed.repository)
    }

    @Test
    fun `git suffix is stripped`() {
        val parsed = parser.parse("https://github.com/owner/repo.git")
        assertNotNull(parsed)
        assertEquals("owner", parsed!!.owner)
        assertEquals("repo", parsed.repository)
    }

    @Test
    fun `scheme-less url is accepted`() {
        val parsed = parser.parse("github.com/owner/repo")
        assertNotNull(parsed)
        assertEquals("owner", parsed!!.owner)
        assertEquals("repo", parsed.repository)
    }

    @Test
    fun `non github host fails gracefully`() {
        assertNull(parser.parse("https://gitlab.com/owner/repo"))
    }

    @Test
    fun `deeper path fails gracefully`() {
        assertNull(parser.parse("https://github.com/owner/repo/releases"))
    }

    @Test
    fun `missing repository fails gracefully`() {
        assertNull(parser.parse("https://github.com/owner"))
    }

    @Test
    fun `empty input fails gracefully`() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("   "))
    }

    @Test
    fun `garbage input fails gracefully`() {
        assertNull(parser.parse("not a url"))
        assertNull(parser.parse("https://github.com//repo"))
        assertNull(parser.parse("ftp://github.com/owner/repo"))
    }

    @Test
    fun `credentials in url are rejected`() {
        assertNull(parser.parse("https://user@github.com/owner/repo"))
    }

    @Test
    fun `non standard port is rejected`() {
        assertNull(parser.parse("https://github.com:8080/owner/repo"))
    }

    @Test
    fun `owner with invalid characters is rejected`() {
        assertNull(parser.parse("https://github.com/ow ner/repo"))
    }
}
