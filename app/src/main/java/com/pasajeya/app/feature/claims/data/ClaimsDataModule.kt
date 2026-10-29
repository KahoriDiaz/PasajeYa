package com.pasajeya.app.feature.claims.data

import com.pasajeya.app.feature.claims.domain.ClaimRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ClaimsDataModule {

    @Binds
    @Singleton
    abstract fun bindClaimRepository(
        impl: FirebaseClaimRepository
    ): ClaimRepository
}