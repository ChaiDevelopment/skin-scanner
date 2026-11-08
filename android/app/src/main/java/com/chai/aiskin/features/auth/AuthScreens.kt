package com.chai.aiskin.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.Eyebrow
import com.chai.aiskin.core.designsystem.NoticeBanner
import com.chai.aiskin.core.designsystem.PrimaryButton
import com.chai.aiskin.core.designsystem.QuietButton
import com.chai.aiskin.core.designsystem.SecondaryButton
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.core.designsystem.Tone
import com.chai.aiskin.core.security.FirebaseStatus
import com.chai.aiskin.features.onboarding.BrandMark

@Composable
fun LoginScreen(onSignedIn: () -> Unit, onRegister: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    AuthScaffold(eyebrow = "Masuk", title = "Selamat datang kembali") {
        ConfigNotice(s, onDevMode = { vm.enterDevMode(onSignedIn) })
        EmailField(s.email, vm::onEmail)
        Spacer(Modifier.height(Spacing.sm))
        PasswordField("Kata sandi", s.password, vm::onPassword, ImeAction.Done, Modifier.testTag("login_password"))
        Messages(s)
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton("Masuk", onClick = { vm.signIn(onSignedIn) }, loading = s.loading, enabled = s.firebaseConfigured, modifier = Modifier.testTag("login_submit"))
        Spacer(Modifier.height(Spacing.xs))
        QuietButton("Lupa kata sandi?", vm::resetPassword)
        Spacer(Modifier.height(Spacing.lg))
        SecondaryButton("Buat akun baru", onRegister)
    }
}

@Composable
fun RegisterScreen(onRegistered: () -> Unit, onBackToLogin: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    AuthScaffold(eyebrow = "Daftar", title = "Buat akun Anda") {
        ConfigNotice(s, onDevMode = { vm.enterDevMode(onRegistered) })
        Field("Nama", s.name, vm::onName, KeyboardOptions(imeAction = ImeAction.Next))
        Spacer(Modifier.height(Spacing.sm))
        EmailField(s.email, vm::onEmail)
        Spacer(Modifier.height(Spacing.sm))
        PasswordField("Kata sandi (min. 8, huruf & angka)", s.password, vm::onPassword, ImeAction.Next)
        Spacer(Modifier.height(Spacing.sm))
        PasswordField("Ulangi kata sandi", s.confirmPassword, vm::onConfirm, ImeAction.Done)
        Messages(s)
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton("Daftar", onClick = { vm.register(onRegistered) }, loading = s.loading, enabled = s.firebaseConfigured)
        Spacer(Modifier.height(Spacing.md))
        QuietButton("Sudah punya akun? Masuk", onBackToLogin)
    }
}

@Composable
private fun AuthScaffold(eyebrow: String, title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xl),
    ) {
        BrandMark(Modifier.size(56.dp))
        Spacer(Modifier.height(Spacing.xl))
        Eyebrow(eyebrow)
        Spacer(Modifier.height(Spacing.xs))
        Text(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(Spacing.xl))
        content()
    }
}

@Composable
private fun ConfigNotice(s: AuthUiState, onDevMode: () -> Unit) {
    if (s.firebaseConfigured) return
    NoticeBanner(FirebaseStatus.MISSING_MESSAGE, tone = Tone.Caution, icon = Icons.Outlined.Info)
    if (s.devModeAllowed) {
        Spacer(Modifier.height(Spacing.sm))
        SecondaryButton("Lanjutkan dengan mode pengembang lokal", onDevMode, icon = Icons.Outlined.Build)
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            "Hanya build debug. Riwayat disimpan sementara di server lokal dan hilang saat server dimatikan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(Spacing.lg))
}

@Composable
private fun Messages(s: AuthUiState) {
    s.error?.let {
        Spacer(Modifier.height(Spacing.sm))
        NoticeBanner(it, tone = Tone.Alert)
    }
    s.info?.let {
        Spacer(Modifier.height(Spacing.sm))
        NoticeBanner(it, tone = Tone.Positive)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AiSkinTheme.extended.accent,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = AiSkinTheme.extended.accent,
)

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardOptions, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = keyboard, shape = MaterialTheme.shapes.medium, colors = fieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) =
    Field("Email", value, onChange, KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), Modifier.testTag("login_email"))

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit, ime: ImeAction, modifier: Modifier = Modifier) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Sembunyikan" else "Tampilkan")
            }
        },
        shape = MaterialTheme.shapes.medium, colors = fieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}
