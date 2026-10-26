package com.pasajeya.app.feature.auth.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.pasajeya.app.BuildConfig
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.CommonPasswordsProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        repository: FirebaseAuthRepository
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(
        repository: FirebaseAccountRepository
    ): AccountRepository

    @Binds
    @Singleton
    abstract fun bindCommonPasswordsProvider(
        provider: AssetCommonPasswordsProvider
    ): CommonPasswordsProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AuthFirebaseModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance(BuildConfig.FUNCTIONS_REGION)
}
