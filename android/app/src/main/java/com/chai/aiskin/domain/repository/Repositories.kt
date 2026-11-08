package com.chai.aiskin.domain.repository

import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.domain.model.AuthState
import com.chai.aiskin.domain.model.AuthUser
import com.chai.aiskin.domain.model.HistoryPage
import com.chai.aiskin.domain.model.ProductSuggestions
import com.chai.aiskin.domain.model.SkinAnalysis
import com.chai.aiskin.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val isFirebaseConfigured: Boolean
    val isDevModeAllowed: Boolean
    suspend fun signIn(email: String, password: String): AppResult<AuthUser>
    suspend fun register(name: String, email: String, password: String): AppResult<AuthUser>
    suspend fun sendPasswordReset(email: String): AppResult<Unit>
    suspend fun enterDevMode(): AppResult<AuthUser>
    suspend fun signOut()
    /** Returns a valid bearer token for the backend, or null when signed out. */
    suspend fun idToken(forceRefresh: Boolean = false): String?
}

interface AnalysisRepository {
    suspend fun analyze(image: File, storePhoto: Boolean): AppResult<SkinAnalysis>
    suspend fun history(cursor: String?, limit: Int): AppResult<HistoryPage>
    suspend fun get(id: String): AppResult<SkinAnalysis>
    suspend fun delete(id: String): AppResult<Unit>
    suspend fun deletePhoto(id: String): AppResult<Unit>
    suspend fun sendFeedback(id: String, helpful: Boolean, rating: Int?, comment: String?): AppResult<Unit>
    /** AI product suggestions for an analysis; cached by the backend unless [refresh]. */
    suspend fun findProducts(id: String, refresh: Boolean): AppResult<ProductSuggestions>
    suspend fun deleteAccount(): AppResult<Unit>
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setOnboardingCompleted(done: Boolean)
    suspend fun setPrivacyConsent(accepted: Boolean)
    suspend fun setStorePhotos(enabled: Boolean)
    suspend fun setDevMode(enabled: Boolean)
    suspend fun clearAccountScoped()
}
