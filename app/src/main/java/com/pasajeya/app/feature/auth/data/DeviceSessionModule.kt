package com.pasajeya.app.feature.auth.data

import com.pasajeya.app.feature.auth.domain.DeviceSessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeviceSessionModule {

    @Provides
    @Singleton
    fun provideDeviceSessionRepository(
        @ApplicationContext context: android.content.Context,
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): DeviceSessionRepository {
        return DeviceSessionRepositoryImpl(context, firestore)
    }
}
