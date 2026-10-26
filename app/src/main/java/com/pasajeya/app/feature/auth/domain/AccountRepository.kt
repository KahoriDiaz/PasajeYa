package com.pasajeya.app.feature.auth.domain

import com.pasajeya.app.feature.auth.domain.model.DeviceSession
import com.pasajeya.app.feature.auth.domain.model.OperatorRegistration
import com.pasajeya.app.feature.auth.domain.model.RemoteSession
import com.pasajeya.app.feature.auth.domain.model.RouteOption
import com.pasajeya.app.feature.auth.domain.model.UserProfile
import com.pasajeya.app.feature.auth.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AccountRepository {
    val deviceSession: StateFlow<DeviceSession?>

    suspend fun ensureSession(uid: String): DeviceSession

    fun clearSession()

    fun observeUserProfile(uid: String): Flow<UserProfile?>

    fun observeRemoteSession(uid: String): Flow<RemoteSession>

    suspend fun loadActiveRoutes(): List<RouteOption>

    suspend fun completeProfile(
        role: UserRole,
        firstName: String,
        lastName: String,
        dni: String,
        birthDate: String,
        consentVersion: String
    )

    suspend fun registerOperator(
        licenseNumber: String,
        licenseClass: String,
        plate: String,
        routeIds: List<String>
    ): OperatorRegistration
}
