package com.pasajeya.app

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PasajeYaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            FirebaseAuth.getInstance().useEmulator(
                BuildConfig.FIREBASE_EMULATOR_HOST,
                BuildConfig.FIREBASE_AUTH_EMULATOR_PORT
            )
            FirebaseFirestore.getInstance().useEmulator(
                BuildConfig.FIREBASE_EMULATOR_HOST,
                BuildConfig.FIRESTORE_EMULATOR_PORT
            )
            FirebaseFunctions.getInstance(
                BuildConfig.FUNCTIONS_REGION
            ).useEmulator(
                BuildConfig.FIREBASE_EMULATOR_HOST,
                BuildConfig.FUNCTIONS_EMULATOR_PORT
            )
        }
    }
}
