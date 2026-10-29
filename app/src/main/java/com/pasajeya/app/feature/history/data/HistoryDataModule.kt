package com.pasajeya.app.feature.history.data

import com.pasajeya.app.feature.history.domain.HistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryDataModule {
    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        repository: FirebaseHistoryRepository
    ): HistoryRepository
}
