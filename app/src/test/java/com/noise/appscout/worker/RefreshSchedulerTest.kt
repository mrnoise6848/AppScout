package com.noise.appscout.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §25 — duplicate scheduling.
 *
 * A fake submitter records every call, so we can assert that repeated scheduling always targets
 * the *same* unique name with the KEEP policy: that combination is what makes duplicates
 * impossible no matter how often it runs.
 */
class RefreshSchedulerTest {

    private class FakeSubmitter : UniquePeriodicWorkSubmitter {
        val submissions = mutableListOf<Pair<String, ExistingPeriodicWorkPolicy>>()
        val removals = mutableListOf<String>()

        override fun submit(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest) {
            submissions += name to policy
        }

        override fun remove(name: String) {
            removals += name
        }
    }

    @Test
    fun `scheduling repeatedly never creates duplicate periodic work`() {
        val submitter = FakeSubmitter()
        val scheduler = RefreshScheduler(submitter)

        repeat(3) { scheduler.schedule() }

        assertEquals(3, submitter.submissions.size)
        assertEquals(1, submitter.submissions.map { it.first }.distinct().size)
        assertEquals(RefreshWorkSpec.WORK_NAME, submitter.submissions.first().first)
        assertTrue(submitter.submissions.all { it.second == ExistingPeriodicWorkPolicy.KEEP })
        assertTrue(RefreshWorkSpec.existingPolicy == ExistingPeriodicWorkPolicy.KEEP)
    }

    @Test
    fun `cancel targets the same unique name`() {
        val submitter = FakeSubmitter()
        val scheduler = RefreshScheduler(submitter)

        scheduler.cancel()

        assertEquals(listOf(RefreshWorkSpec.WORK_NAME), submitter.removals)
    }

    @Test
    fun `refresh job requires a connection and runs at most twice a day`() {
        val constraints = RefreshWorkSpec.constraints()
        assertEquals(androidx.work.NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertEquals(12L, RefreshWorkSpec.INTERVAL_HOURS)
    }

    @Test
    fun `the periodic request can be built without an Android runtime`() {
        // Ensures the request builder stays unit-testable and carries the expected constraints.
        val request: PeriodicWorkRequest = RefreshWorkSpec.request<ReleaseRefreshWorker>()
        assertEquals(
            androidx.work.NetworkType.CONNECTED,
            request.workSpec.constraints.requiredNetworkType,
        )
        assertEquals(
            RefreshWorkSpec.INTERVAL_HOURS * 60 * 60 * 1000L,
            request.workSpec.intervalDuration,
        )
    }
}
