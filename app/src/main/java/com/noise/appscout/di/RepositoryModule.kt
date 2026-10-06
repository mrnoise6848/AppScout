package com.noise.appscout.di

import com.noise.appscout.data.local.PackageManagerInstalledAppRepository
import com.noise.appscout.data.local.SettingsDataStore
import com.noise.appscout.data.remote.github.GitHubSourceUrlParser
import com.noise.appscout.data.repository.ReleaseRepositoryImpl
import com.noise.appscout.data.repository.SettingsRepositoryImpl
import com.noise.appscout.data.repository.TrackedAppRepositoryImpl
import com.noise.appscout.domain.repository.InstalledAppRepository
import com.noise.appscout.domain.repository.ReleaseRepository
import com.noise.appscout.domain.repository.SettingsRepository
import com.noise.appscout.domain.repository.TrackedAppRepository
import com.noise.appscout.domain.model.SourceUrlParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindInstalledAppRepository(
        impl: PackageManagerInstalledAppRepository,
    ): InstalledAppRepository

    @Binds
    @Singleton
    abstract fun bindTrackedAppRepository(
        impl: TrackedAppRepositoryImpl,
    ): TrackedAppRepository

    @Binds
    @Singleton
    abstract fun bindReleaseRepository(
        impl: ReleaseRepositoryImpl,
    ): ReleaseRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl,
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSourceUrlParser(
        impl: GitHubSourceUrlParser,
    ): SourceUrlParser

    @Binds
    @Singleton
    abstract fun bindReleaseNotifier(
        impl: com.noise.appscout.core.notification.AndroidReleaseNotifier,
    ): com.noise.appscout.domain.service.ReleaseNotifier

    @Binds
    @Singleton
    abstract fun bindAiSummaryRepository(
        impl: com.noise.appscout.data.repository.AiSummaryRepositoryImpl,
    ): com.noise.appscout.domain.repository.AiSummaryRepository

    @Binds
    @Singleton
    abstract fun bindAiApiKeyProvider(
        impl: com.noise.appscout.core.security.ApiKeyVault,
    ): com.noise.appscout.domain.ai.AiApiKeyProvider

    /** The only production AI implementation; tests substitute a fake at construction time. */
    @Binds
    @Singleton
    abstract fun bindAiSummaryProvider(
        impl: com.noise.appscout.data.remote.gemini.GeminiAiSummaryProvider,
    ): com.noise.appscout.domain.ai.AiSummaryProvider
}
