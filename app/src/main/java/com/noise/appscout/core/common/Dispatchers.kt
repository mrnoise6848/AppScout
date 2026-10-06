package com.noise.appscout.core.common

import javax.inject.Qualifier

/** Marks a [kotlinx.coroutines.CoroutineDispatcher] used for I/O bound work (disk, network). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Marks a [kotlinx.coroutines.CoroutineDispatcher] used for CPU bound work (parsing, sorting). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
