package org.operatorfoundation.audiowavedemo

import android.app.Application
import timber.log.Timber

/**
 * Application class for initializing app-wide components.
 */
class AudioWaveApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        Timber.plant(Timber.DebugTree())
    }
}