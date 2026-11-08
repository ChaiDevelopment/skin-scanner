package com.chai.aiskin.core.security

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * DEBUG builds use the App Check debug provider. On first run Logcat prints a debug
 * token; register it in Firebase Console → App Check → Manage debug tokens.
 */
object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
