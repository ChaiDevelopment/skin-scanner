package com.chai.aiskin.domain.model

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    /** True only for the explicit debug-build local development mode (no Firebase). */
    val isDevMode: Boolean = false,
)

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val privacyConsentAccepted: Boolean = false,
    val storePhotos: Boolean = false,
    val devModeEnabled: Boolean = false,
)
