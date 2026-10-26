package com.pasajeya.app.feature.auth.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.AuthException
import com.pasajeya.app.feature.auth.domain.model.AuthFailure
import com.pasajeya.app.feature.auth.domain.model.AuthUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth
) : AuthRepository {
    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toDomain())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun currentUser(): AuthUser? = auth.currentUser?.toDomain()

    override suspend fun signIn(email: String, password: String): AuthUser {
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password)
                .await()
                .user
                ?.toDomain()
                ?: throw AuthException(AuthFailure.Unknown)
        } catch (error: FirebaseAuthException) {
            throw AuthException(error.toFailure())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw AuthException(AuthFailure.Unknown)
        }
    }

    override suspend fun register(email: String, password: String): AuthUser {
        return try {
            auth.createUserWithEmailAndPassword(email.trim(), password)
                .await()
                .user
                ?.toDomain()
                ?: throw AuthException(AuthFailure.Unknown)
        } catch (error: FirebaseAuthException) {
            throw AuthException(error.toFailure())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw AuthException(AuthFailure.Unknown)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun sendEmailVerification() {
        val user = auth.currentUser ?: throw AuthException(AuthFailure.NotSignedIn)
        try {
            user.sendEmailVerification().await()
        } catch (error: FirebaseAuthException) {
            throw AuthException(error.toFailure())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw AuthException(AuthFailure.Unknown)
        }
    }

    override suspend fun reloadUser(): AuthUser {
        val user = auth.currentUser ?: throw AuthException(AuthFailure.NotSignedIn)
        try {
            user.reload().await()
            user.getIdToken(true).await()
            return user.toDomain()
        } catch (error: FirebaseAuthException) {
            throw AuthException(error.toFailure())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw AuthException(AuthFailure.Unknown)
        }
    }

    override suspend fun sendPasswordReset(email: String) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
        } catch (error: FirebaseAuthException) {
            throw AuthException(error.toFailure())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw AuthException(AuthFailure.Unknown)
        }
    }

    private fun FirebaseUser.toDomain(): AuthUser = AuthUser(
        uid = uid,
        email = email.orEmpty(),
        emailVerified = isEmailVerified
    )

    private fun FirebaseAuthException.toFailure(): AuthFailure = when (errorCode) {
        "ERROR_USER_NOT_FOUND",
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIALS" -> AuthFailure.InvalidCredentials
        "ERROR_EMAIL_ALREADY_IN_USE" -> AuthFailure.EmailAlreadyInUse
        "ERROR_WEAK_PASSWORD" -> AuthFailure.WeakPassword
        "ERROR_NETWORK_REQUEST_FAILED" -> AuthFailure.Network
        "ERROR_TOO_MANY_REQUESTS" -> AuthFailure.TooManyRequests
        "ERROR_REQUIRES_RECENT_LOGIN" -> AuthFailure.RequiresRecentLogin
        else -> AuthFailure.Unknown
    }
}
