package com.noise.appscout.domain.version

import org.junit.Assert.assertEquals
import org.junit.Test

/** Acceptance tests C, D and the ordering rules around prereleases. */
class VersionComparatorTest {

    private fun cmp(a: String?, b: String?) = VersionComparator.compare(a, b)

    @Test
    fun `numeric ordering`() {
        assertEquals(VersionComparison.LOWER, cmp("1.2.0", "1.3.0"))
        assertEquals(VersionComparison.GREATER, cmp("1.3.0", "1.2.0"))
        assertEquals(VersionComparison.EQUAL, cmp("1.2.3", "1.2.3"))
    }

    @Test
    fun `prefix differences do not change ordering`() {
        assertEquals(VersionComparison.EQUAL, cmp("v1.2.3", "1.2.3"))
        assertEquals(VersionComparison.LOWER, cmp("v1.9.0", "2.0.0"))
    }

    @Test
    fun `missing segments are treated as zero`() {
        assertEquals(VersionComparison.EQUAL, cmp("1.2", "1.2.0"))
        assertEquals(VersionComparison.LOWER, cmp("1.2", "1.2.1"))
    }

    @Test
    fun `unparsable versions are uncertain, never a false update`() {
        assertEquals(VersionComparison.UNCERTAIN, cmp("2026.09", "stable-latest"))
        assertEquals(VersionComparison.UNCERTAIN, cmp(null, "1.0.0"))
        assertEquals(VersionComparison.UNCERTAIN, cmp("1.0.0", null))
        assertEquals(VersionComparison.UNCERTAIN, cmp("stable-latest", "nightly"))
    }

    @Test
    fun `prerelease sorts below its stable release`() {
        assertEquals(VersionComparison.LOWER, cmp("v2.0.0-beta.1", "v2.0.0"))
        assertEquals(VersionComparison.GREATER, cmp("v2.0.0", "v2.0.0-beta.1"))
    }

    @Test
    fun `prerelease identifiers follow semver ordering`() {
        assertEquals(VersionComparison.LOWER, cmp("1.0.0-alpha", "1.0.0-alpha.1"))
        assertEquals(VersionComparison.LOWER, cmp("1.0.0-alpha.1", "1.0.0-alpha.beta"))
        assertEquals(VersionComparison.LOWER, cmp("1.0.0-alpha.beta", "1.0.0-beta"))
        assertEquals(VersionComparison.LOWER, cmp("1.0.0-beta.2", "1.0.0-beta.11"))
        assertEquals(VersionComparison.LOWER, cmp("1.0.0-rc.1", "1.0.0"))
    }
}
