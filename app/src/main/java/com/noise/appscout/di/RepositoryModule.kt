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
}
