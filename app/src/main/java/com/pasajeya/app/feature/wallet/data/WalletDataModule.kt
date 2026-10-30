package com.pasajeya.app.feature.wallet.data

import com.pasajeya.app.feature.wallet.domain.WalletRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WalletDataModule {
    @Binds
    @Singleton
    abstract fun bindWalletRepository(repository: DataStoreWalletRepository): WalletRepository
}
