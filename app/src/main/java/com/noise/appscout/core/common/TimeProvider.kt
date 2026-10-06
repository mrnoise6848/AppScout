package com.noise.appscout.core.common

/** Injectable time source so that caching, staleness and notifications stay testable. */
interface TimeProvider {
    fun nowMillis(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
