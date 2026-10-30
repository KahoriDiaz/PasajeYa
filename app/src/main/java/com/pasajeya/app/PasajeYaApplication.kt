package com.pasajeya.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PasajeYaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Emuladores desconectados para migración a Firebase Cloud real
    }
}
