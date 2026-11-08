package com.chai.aiskin.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chai.aiskin.domain.model.AuthState
import com.chai.aiskin.domain.repository.AuthRepository
import com.chai.aiskin.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Pure start-destination rule (unit-tested). */
object StartDestination {
    fun resolve(onboardingDone: Boolean, consentAccepted: Boolean, signedIn: Boolean, cameraGranted: Boolean): Route = when {
        !onboardingDone || !consentAccepted -> Route.Onboarding
        !signedIn -> Route.Login
        cameraGranted -> Route.Camera // prioritize direct access to the front camera
        else -> Route.Home
    }
}

@HiltViewModel
class StartViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    auth: AuthRepository,
    prefs: PreferencesRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = auth.authState

    /** Scanning requires privacy consent; revoking it in Settings blocks the camera. */
    val consentAccepted: StateFlow<Boolean> = prefs.preferences
        .map { it.privacyConsentAccepted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** null while still loading. */
    val startDestination: StateFlow<Route?> = combine(auth.authState, prefs.preferences) { a, p ->
        if (a is AuthState.Loading) null
        else StartDestination.resolve(p.onboardingCompleted, p.privacyConsentAccepted, a is AuthState.SignedIn, cameraGranted())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun cameraGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
