package com.noise.appscout.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Narrow port for submitting the unique periodic refresh job.
 *
 * Extracting it keeps [RefreshScheduler] testable without an Android WorkManager instance: the
 * unit test asserts *what* is submitted (unique name + KEEP), the implementation submits it.
 */
interface UniquePeriodicWorkSubmitter {
    fun submit(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest)
    fun remove(name: String)
}

class WorkManagerSubmitter @Inject constructor(
    private val workManager: WorkManager,
) : UniquePeriodicWorkSubmitter {

    override fun submit(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest) {
        workManager.enqueueUniquePeriodicWork(name, policy, request)
    }

    override fun remove(name: String) {
        workManager.cancelUniqueWork(name)
    }
}

/**
 * Owns the unique periodic refresh job.
 *
 * Every call uses the same unique work name with [ExistingPeriodicWorkPolicy.KEEP], so scheduling
 * repeatedly (app start, settings toggle, process restart) can never create duplicate periodic
 * work — the requirement of spec §25.
 */
@Singleton
class RefreshScheduler @Inject constructor(
    private val submitter: UniquePeriodicWorkSubmitter,
) {

    fun schedule(request: PeriodicWorkRequest = RefreshWorkSpec.request<ReleaseRefreshWorker>()) {
        submitter.submit(RefreshWorkSpec.WORK_NAME, RefreshWorkSpec.existingPolicy, request)
    }

    fun cancel() {
        submitter.remove(RefreshWorkSpec.WORK_NAME)
    }
}
