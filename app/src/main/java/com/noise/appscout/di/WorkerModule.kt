package com.noise.appscout.di

import android.content.Context
import androidx.work.WorkManager
import com.noise.appscout.worker.UniquePeriodicWorkSubmitter
import com.noise.appscout.worker.WorkManagerSubmitter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkerProvidesModule {

    /**
     * WorkManager is initialized on demand through the application's `Configuration.Provider`
     * (Hilt worker factory), so `getInstance` is safe here.
     */
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerBindingsModule {

    @Binds
    @Singleton
    abstract fun bindPeriodicWorkSubmitter(impl: WorkManagerSubmitter): UniquePeriodicWorkSubmitter
}
