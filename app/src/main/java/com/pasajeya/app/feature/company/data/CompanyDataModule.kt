package com.pasajeya.app.feature.company.data

import com.pasajeya.app.feature.company.domain.CompanyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CompanyDataModule {
    @Binds
    @Singleton
    abstract fun bindCompanyRepository(
        repository: FirebaseCompanyRepository
    ): CompanyRepository
}
