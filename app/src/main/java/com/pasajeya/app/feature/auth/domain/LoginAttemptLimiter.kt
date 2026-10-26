package com.pasajeya.app.feature.auth.domain

import kotlin.math.ceil

class LoginAttemptLimiter(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxAttempts: Int = 5,
    private val windowMillis: Long = 30_000L,
    private val lockMillis: Long = 30_000L
) {
    private val failures = ArrayDeque<Long>()
    private var lockedUntilMillis = 0L

    fun isLocked(): Boolean = remainingSeconds() > 0

    fun remainingSeconds(): Int {
        val remaining = lockedUntilMillis - nowMillis()
        return if (remaining <= 0L) 0 else ceil(remaining / 1_000.0).toInt()
    }

    fun recordFailure(): Int {
        val now = nowMillis()

        if (lockedUntilMillis > now) {
            return remainingSeconds()
        }

        lockedUntilMillis = 0L
        failures.removeAll { failure -> now - failure > windowMillis }
        failures.addLast(now)

        if (failures.size >= maxAttempts) {
            failures.clear()
            lockedUntilMillis = now + lockMillis
        }

        return remainingSeconds()
    }

    fun reset() {
        failures.clear()
        lockedUntilMillis = 0L
    }
}
