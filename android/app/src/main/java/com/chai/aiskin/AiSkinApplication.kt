package com.chai.aiskin

import android.app.Application
import android.util.Log
import com.chai.aiskin.core.security.AppCheckInstaller
import com.chai.aiskin.core.security.FirebaseStatus
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AiSkinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseStatus.isConfigured(this)) {
            runCatching { AppCheckInstaller.install() }
                .onFailure { Log.w(TAG, "App Check could not be installed: ${it.javaClass.simpleName}") }
        } else {
            Log.w(TAG, FirebaseStatus.MISSING_MESSAGE)
        }
    }

    private companion object {
        const val TAG = "AiSkin"
    }
}
