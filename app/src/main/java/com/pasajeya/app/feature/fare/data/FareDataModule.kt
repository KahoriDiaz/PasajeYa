package com.pasajeya.app.feature.fare.data

import com.pasajeya.app.feature.fare.domain.FareRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FareDataModule {
    @Binds
    @Singleton
    abstract fun bindFareRepository(repository: FirebaseFareRepository): FareRepository
}
