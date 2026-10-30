package com.pasajeya.app.feature.voice.data

import com.pasajeya.app.feature.voice.domain.SpeechAnnouncer
import com.pasajeya.app.feature.voice.domain.VoicePreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceDataModule {

    @Binds
    @Singleton
    abstract fun bindSpeechAnnouncer(
        announcer: AndroidSpeechAnnouncer
    ): SpeechAnnouncer

    @Binds
    @Singleton
    abstract fun bindVoicePreferences(
        preferences: DataStoreVoicePreferences
    ): VoicePreferences
}
