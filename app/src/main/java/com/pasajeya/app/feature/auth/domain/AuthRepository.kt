package com.pasajeya.app.feature.auth.domain

import com.pasajeya.app.feature.auth.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthUser?>

    fun currentUser(): AuthUser?

    suspend fun signIn(email: String, password: String): AuthUser

    suspend fun register(email: String, password: String): AuthUser

    suspend fun signOut()

    suspend fun sendEmailVerification()

    suspend fun reloadUser(): AuthUser

    suspend fun sendPasswordReset(email: String)
}
