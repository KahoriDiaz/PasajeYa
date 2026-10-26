package com.pasajeya.app.feature.auth.domain

interface CommonPasswordsProvider {
    suspend fun get(): Set<String>
}
