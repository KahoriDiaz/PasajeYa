package com.pasajeya.app.feature.claims.data

import dagger.Module
import dagger.Provides
import hilt.android.lifecycle.ProvidesAndroidViewModel
import javax.inject.Singleton

@Module
@Singleton
object ClaimsDataModule {

    @Provides
    @Singleton
    fun provideClaimRepository(): ClaimRepository = FirebaseClaimRepository()

    @Provides
    @Singleton
    @HiltViewModel
    fun provideClaimViewModel(): ClaimViewModel = ClaimViewModel(
        claimRepository = provideClaimRepository()
    )
}