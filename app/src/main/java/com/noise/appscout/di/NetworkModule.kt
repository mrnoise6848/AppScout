package com.noise.appscout.di

import com.noise.appscout.core.network.HttpClients
import com.noise.appscout.core.network.NetworkConfig
import com.noise.appscout.data.remote.gemini.GeminiApi
import com.noise.appscout.data.remote.github.GitHubApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        HttpClients.okHttpClient(HttpClients.githubInterceptors())

    @GitHubRetrofit
    @Provides
    @Singleton
    fun provideGitHubRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(NetworkConfig.GITHUB_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @GeminiRetrofit
    @Provides
    @Singleton
    fun provideGeminiRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(NetworkConfig.GEMINI_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideGitHubApi(@GitHubRetrofit retrofit: Retrofit): GitHubApi =
        retrofit.create(GitHubApi::class.java)

    @Provides
    @Singleton
    fun provideGeminiApi(@GeminiRetrofit retrofit: Retrofit): GeminiApi =
        retrofit.create(GeminiApi::class.java)
}
