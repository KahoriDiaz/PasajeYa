package com.pasajeya.app.feature.trip.data

import com.pasajeya.app.feature.trip.domain.TripRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TripDataModule {
    @Binds
    @Singleton
    abstract fun bindTripRepository(
        repository: FirebaseTripRepository
    ): TripRepository
}
