package com.chai.aiskin.core.security

import android.content.Context
import com.chai.aiskin.BuildConfig
import com.google.firebase.FirebaseApp

/**
 * Firebase is initialized automatically from google-services.json. When that file
 * is missing the app must not touch Firebase APIs (they would throw), and instead
 * reports the missing configuration.
 */
object FirebaseStatus {
    fun isConfigured(context: Context): Boolean =
        BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()

    const val MISSING_MESSAGE =
        "Firebase belum dikonfigurasi. Tambahkan android/app/google-services.json lalu build ulang (lihat docs/FIREBASE_SETUP.md)."
}
