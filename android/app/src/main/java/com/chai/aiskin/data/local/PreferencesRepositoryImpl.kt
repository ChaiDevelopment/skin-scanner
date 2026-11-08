package com.chai.aiskin.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.chai.aiskin.domain.model.UserPreferences
import com.chai.aiskin.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_skin_prefs")

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferencesRepository {

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val PRIVACY = booleanPreferencesKey("privacy_consent_accepted")
        /** Default false: photos are NOT stored unless the user explicitly opts in. */
        val STORE_PHOTOS = booleanPreferencesKey("store_photos")
        val DEV_MODE = booleanPreferencesKey("dev_mode_enabled")
    }

    override val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            UserPreferences(
                onboardingCompleted = p[Keys.ONBOARDING] ?: false,
                privacyConsentAccepted = p[Keys.PRIVACY] ?: false,
                storePhotos = p[Keys.STORE_PHOTOS] ?: false,
                devModeEnabled = p[Keys.DEV_MODE] ?: false,
            )
        }

    override suspend fun setOnboardingCompleted(done: Boolean) { context.dataStore.edit { it[Keys.ONBOARDING] = done } }
    override suspend fun setPrivacyConsent(accepted: Boolean) { context.dataStore.edit { it[Keys.PRIVACY] = accepted } }
    override suspend fun setStorePhotos(enabled: Boolean) { context.dataStore.edit { it[Keys.STORE_PHOTOS] = enabled } }
    override suspend fun setDevMode(enabled: Boolean) { context.dataStore.edit { it[Keys.DEV_MODE] = enabled } }

    override suspend fun clearAccountScoped() {
        context.dataStore.edit {
            it.remove(Keys.STORE_PHOTOS)
            it.remove(Keys.DEV_MODE)
        }
    }
}
