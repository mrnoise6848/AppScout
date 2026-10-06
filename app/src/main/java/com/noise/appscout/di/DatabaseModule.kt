package com.noise.appscout.di

import android.content.Context
import androidx.room.Room
import com.noise.appscout.core.common.SystemTimeProvider
import com.noise.appscout.core.common.TimeProvider
import com.noise.appscout.core.database.AppScoutDatabase
import com.noise.appscout.core.database.dao.AiSummaryDao
import com.noise.appscout.core.database.dao.ReleaseDao
import com.noise.appscout.core.database.dao.SourceDao
import com.noise.appscout.core.database.dao.TrackedAppDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppScoutDatabase =
        Room.databaseBuilder(context, AppScoutDatabase::class.java, AppScoutDatabase.NAME)
            .build()

    @Provides
    fun provideTrackedAppDao(db: AppScoutDatabase): TrackedAppDao = db.trackedAppDao()

    @Provides
    fun provideSourceDao(db: AppScoutDatabase): SourceDao = db.sourceDao()

    @Provides
    fun provideReleaseDao(db: AppScoutDatabase): ReleaseDao = db.releaseDao()

    @Provides
    fun provideAiSummaryDao(db: AppScoutDatabase): AiSummaryDao = db.aiSummaryDao()
}

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()
}
