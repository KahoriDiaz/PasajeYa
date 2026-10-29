package com.pasajeya.app.feature.trip.domain

class QrScanGate(
    private val clock: () -> Long = System::currentTimeMillis,
    private val duplicateWindowMillis: Long = 3_000L
) {
    private var lastValue: String? = null
    private var lastAcceptedAt = 0L

    @Synchronized
    fun accept(rawValue: String): Boolean {
        val value = rawValue.trim()
        if (value.isEmpty()) return false
        val now = clock()
        if (value == lastValue && now - lastAcceptedAt < duplicateWindowMillis) {
            return false
        }
        lastValue = value
        lastAcceptedAt = now
        return true
    }
}
