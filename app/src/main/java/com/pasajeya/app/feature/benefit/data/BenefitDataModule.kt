package com.pasajeya.app.feature.benefit.data

import com.pasajeya.app.feature.benefit.domain.BenefitRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BenefitDataModule {

    @Binds
    @Singleton
    abstract fun bindBenefitRepository(
        impl: FirebaseBenefitRepository
    ): BenefitRepository
}