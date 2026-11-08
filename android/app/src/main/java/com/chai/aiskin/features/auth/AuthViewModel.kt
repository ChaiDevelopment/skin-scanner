package com.chai.aiskin.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.core.security.FirebaseStatus
import com.chai.aiskin.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val firebaseConfigured: Boolean = true,
    val devModeAllowed: Boolean = false,
)

object AuthValidation {
    fun email(email: String): String? = when {
        email.isBlank() -> "Email wajib diisi."
        !EMAIL.matches(email.trim()) -> "Format email tidak valid."
        else -> null
    }

    fun password(pw: String): String? = when {
        pw.length < 8 -> "Kata sandi minimal 8 karakter."
        pw.none { it.isDigit() } || pw.none { it.isLetter() } -> "Kata sandi harus berisi huruf dan angka."
        else -> null
    }

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
}

@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthUiState(firebaseConfigured = auth.isFirebaseConfigured, devModeAllowed = auth.isDevModeAllowed),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onConfirm(v: String) = _state.update { it.copy(confirmPassword = v, error = null) }
    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    fun signIn(onSuccess: () -> Unit) {
        val s = _state.value
        if (!s.firebaseConfigured) return fail(FirebaseStatus.MISSING_MESSAGE)
        AuthValidation.email(s.email)?.let { return fail(it) }
        if (s.password.isEmpty()) return fail("Kata sandi wajib diisi.")
        launchResult({ auth.signIn(s.email, s.password) }, onSuccess)
    }

    fun register(onSuccess: () -> Unit) {
        val s = _state.value
        if (!s.firebaseConfigured) return fail(FirebaseStatus.MISSING_MESSAGE)
        if (s.name.isBlank()) return fail("Nama wajib diisi.")
        AuthValidation.email(s.email)?.let { return fail(it) }
        AuthValidation.password(s.password)?.let { return fail(it) }
        if (s.password != s.confirmPassword) return fail("Konfirmasi kata sandi tidak sama.")
        launchResult({ auth.register(s.name, s.email, s.password) }, onSuccess)
    }

    fun resetPassword() {
        val s = _state.value
        if (!s.firebaseConfigured) return fail(FirebaseStatus.MISSING_MESSAGE)
        AuthValidation.email(s.email)?.let { return fail("Masukkan email Anda terlebih dahulu. $it") }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = auth.sendPasswordReset(s.email)) {
                is AppResult.Success -> _state.update { it.copy(loading = false, info = "Tautan atur ulang kata sandi telah dikirim ke ${s.email.trim()}.") }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = r.error.message) }
            }
        }
    }

    fun enterDevMode(onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val r = auth.enterDevMode()) {
                is AppResult.Success -> onSuccess()
                is AppResult.Failure -> fail(r.error.message)
            }
        }
    }


    private fun <T> launchResult(block: suspend () -> AppResult<T>, onSuccess: () -> Unit) {
        if (_state.value.loading) return // prevent double submission
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = block()) {
                is AppResult.Success -> {
                    _state.update { it.copy(loading = false, password = "", confirmPassword = "") }
                    onSuccess()
                }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = r.error.message) }
            }
        }
    }

    private fun fail(msg: String) = _state.update { it.copy(error = msg) }
}

