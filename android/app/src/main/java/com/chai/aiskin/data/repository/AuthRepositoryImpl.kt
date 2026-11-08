package com.chai.aiskin.data.repository

import android.content.Context
import com.chai.aiskin.BuildConfig
import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.core.constants.AppConstants
import com.chai.aiskin.core.network.TokenProvider
import com.chai.aiskin.core.security.FirebaseStatus
import com.chai.aiskin.di.ApplicationScope
import com.chai.aiskin.domain.model.AuthState
import com.chai.aiskin.domain.model.AuthUser
import com.chai.aiskin.domain.repository.AuthRepository
import com.chai.aiskin.domain.repository.PreferencesRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val prefs: PreferencesRepository,
    @ApplicationScope scope: CoroutineScope,
) : AuthRepository, TokenProvider {

    override val isFirebaseConfigured: Boolean = FirebaseStatus.isConfigured(context)
    override val isDevModeAllowed: Boolean = BuildConfig.DEV_MODE_ALLOWED && !isFirebaseConfigured

    private val auth: FirebaseAuth? by lazy { if (isFirebaseConfigured) FirebaseAuth.getInstance() else null }
    private val firestore: FirebaseFirestore? by lazy { if (isFirebaseConfigured) FirebaseFirestore.getInstance() else null }

    private val firebaseUser = auth?.let { a ->
        callbackFlow<FirebaseUser?> {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
            a.addAuthStateListener(listener)
            awaitClose { a.removeAuthStateListener(listener) }
        }
    } ?: flowOf<FirebaseUser?>(null)

    override val authState: StateFlow<AuthState> = combine(firebaseUser, prefs.preferences) { user, p ->
        when {
            user != null -> AuthState.SignedIn(user.toDomain())
            isDevModeAllowed && p.devModeEnabled -> AuthState.SignedIn(DEV_USER)
            else -> AuthState.SignedOut
        }
    }.stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    override suspend fun signIn(email: String, password: String): AppResult<AuthUser> = firebase { a ->
        val user = a.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: return@firebase AppResult.Failure(AppError.Auth("Gagal masuk. Silakan coba lagi."))
        AppResult.Success(user.toDomain())
    }

    override suspend fun register(name: String, email: String, password: String): AppResult<AuthUser> = firebase { a ->
        val user = a.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: return@firebase AppResult.Failure(AppError.Auth("Gagal mendaftar. Silakan coba lagi."))
        val displayName = name.trim().ifBlank { email.substringBefore('@') }.take(80)
        user.updateProfile(userProfileChangeRequest { this.displayName = displayName }).await()
        // users/{uid} profile document; matches firebase/firestore.rules (allowed keys only).
        runCatching {
            firestore?.collection("users")?.document(user.uid)?.set(
                mapOf(
                    "uid" to user.uid,
                    "displayName" to displayName,
                    "email" to user.email,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )?.await()
        }
        AppResult.Success(user.toDomain().copy(displayName = displayName))
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = firebase { a ->
        a.sendPasswordResetEmail(email.trim()).await()
        AppResult.Success(Unit)
    }

    override suspend fun enterDevMode(): AppResult<AuthUser> {
        if (!isDevModeAllowed) return AppResult.Failure(AppError.Auth("Mode pengembang tidak tersedia."))
        prefs.setDevMode(true)
        return AppResult.Success(DEV_USER)
    }

    override suspend fun signOut() {
        auth?.signOut()
        prefs.setDevMode(false)
    }

    override suspend fun idToken(forceRefresh: Boolean): String? {
        val user = auth?.currentUser
        if (user != null) {
            return runCatching { user.getIdToken(forceRefresh).await().token }.getOrNull()
        }
        return if (isDevModeAllowed && prefs.preferences.first().devModeEnabled) AppConstants.DEV_TOKEN else null
    }

    override suspend fun token(forceRefresh: Boolean): String? = idToken(forceRefresh)

    override suspend fun appCheckToken(): String? {
        if (!isFirebaseConfigured) return null
        return withTimeoutOrNull(3_000) {
            runCatching { FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token }.getOrNull()
        }
    }

    private suspend fun <T> firebase(block: suspend (FirebaseAuth) -> AppResult<T>): AppResult<T> {
        val a = auth ?: return AppResult.Failure(AppError.NotConfigured(FirebaseStatus.MISSING_MESSAGE))
        return try {
            block(a)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(mapFirebaseError(e))
        }
    }

    private fun FirebaseUser.toDomain() = AuthUser(uid = uid, email = email, displayName = displayName)

    companion object {
        val DEV_USER = AuthUser(uid = "dev-local-user", email = "dev@localhost", displayName = "Mode Pengembang", isDevMode = true)

        fun mapFirebaseError(e: Exception): AppError = when (e) {
            is FirebaseAuthWeakPasswordException -> AppError.Auth("Kata sandi terlalu lemah. Gunakan minimal 6 karakter.")
            is FirebaseAuthUserCollisionException -> AppError.Auth("Email ini sudah terdaftar. Silakan masuk.")
            is FirebaseAuthInvalidUserException -> AppError.Auth("Akun tidak ditemukan atau telah dinonaktifkan.")
            is FirebaseAuthInvalidCredentialsException -> AppError.Auth("Email atau kata sandi tidak sesuai.")
            is FirebaseTooManyRequestsException -> AppError.RateLimited("Terlalu banyak percobaan. Coba lagi nanti.")
            is FirebaseNetworkException -> AppError.Network()
            else -> AppError.Auth("Autentikasi gagal. Silakan coba lagi.")
        }
    }
}
