package com.noise.appscout.core.network

import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/** Builds the shared HTTP configuration used by every remote call. */
object HttpClients {

    /** GitHub asks clients to identify themselves; no credentials are ever attached here. */
    fun githubInterceptors(): List<Interceptor> = listOf(
        Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", NetworkConfig.USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            chain.proceed(request)
        },
    )

    fun okHttpClient(interceptors: List<Interceptor> = emptyList()): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(NetworkConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply { interceptors.forEach { addInterceptor(it) } }
            .build()
}
