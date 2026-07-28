package dev.aura.auradroid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AuraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
