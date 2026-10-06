package com.noise.appscout

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.noise.appscout.core.notification.NotificationChannels
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.worker.RefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class AppScoutApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var refreshScheduler: RefreshScheduler

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        syncBackgroundRefresh()
    }

    /**
     * Enqueues (or removes) the unique periodic refresh job to match the persisted preference.
     * Using the unique name with `KEEP` makes this safe to call on every process start.
     */
    private fun syncBackgroundRefresh() {
        applicationScope.launch {
            runCatching {
                if (settingsRepository.getSettings().backgroundRefreshEnabled) {
                    refreshScheduler.schedule()
                } else {
                    refreshScheduler.cancel()
                }
            }
        }
    }
}
