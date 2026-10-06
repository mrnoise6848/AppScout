package com.noise.appscout.domain.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance test B — version normalization. */
class VersionParserTest {

    @Test
    fun `bare and v-prefixed tags normalize identically`() {
        assertEquals("1.2.3", VersionParser.normalize("1.2.3"))
        assertEquals("1.2.3", VersionParser.normalize("v1.2.3"))
        assertEquals("1.2.3", VersionParser.normalize("V1.2.3"))
    }

    @Test
    fun `known prefixes are stripped`() {
        assertEquals("1.2.3", VersionParser.normalize("release-1.2.3"))
        assertEquals("1.2.3", VersionParser.normalize("rel-1.2.3"))
        assertEquals("1.2.3", VersionParser.normalize("version-1.2.3"))
        assertEquals("1.2.3", VersionParser.normalize("ver-1.2.3"))
    }

    @Test
    fun `two segment versions are supported`() {
        assertEquals("2026.9", VersionParser.normalize("v2026.09"))
    }

    @Test
    fun `single number version is supported`() {
        assertEquals("42", VersionParser.normalize("42"))
    }

    @Test
    fun `prerelease suffix is normalized to lowercase`() {
        assertEquals("2.0.0-beta.1", VersionParser.normalize("v2.0.0-beta.1"))
        assertEquals("1.0.0-rc.1", VersionParser.normalize("1.0.0_RC_1"))
    }

    @Test
    fun `build metadata never affects ordering`() {
        assertEquals("1.2.3", VersionParser.normalize("1.2.3+build.99"))
        val left = VersionParser.parse("1.2.3+build.99")!!
        val right = VersionParser.parse("1.2.3+build.1")!!
        assertEquals(VersionComparison.EQUAL, VersionComparator.compare(left, right))
    }

    @Test
    fun `stable flag is exposed`() {
        assertTrue(VersionParser.parse("1.2.3")!!.isStable)
        assertFalse(VersionParser.parse("1.2.3-rc.1")!!.isStable)
    }

    @Test
    fun `ambiguous tags do not normalize`() {
        assertNull(VersionParser.normalize(""))
        assertNull(VersionParser.normalize(null))
        assertNull(VersionParser.normalize("stable-latest"))
        assertNull(VersionParser.normalize("latest"))
        assertNull(VersionParser.normalize("v"))
        assertNull(VersionParser.normalize("1.2.3 (final)"))
        assertNull(VersionParser.normalize("build 123"))
        assertNull(VersionParser.normalize("1.2.3-"))
    }
}
