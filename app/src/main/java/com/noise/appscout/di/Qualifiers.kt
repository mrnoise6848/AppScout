package com.noise.appscout.di

import javax.inject.Qualifier

/** Two Retrofit instances exist (GitHub + Gemini); both are qualified to avoid ambiguity. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiRetrofit
