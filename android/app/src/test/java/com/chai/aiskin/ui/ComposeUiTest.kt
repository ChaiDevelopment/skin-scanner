package com.chai.aiskin.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chai.aiskin.FakePreferencesRepository
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.EmptyView
import com.chai.aiskin.core.designsystem.ErrorView
import com.chai.aiskin.core.designsystem.LoadingView
import com.chai.aiskin.features.onboarding.OnboardingScreen
import com.chai.aiskin.features.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ComposeUiTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `onboarding start button requires privacy consent`() {
        val prefs = FakePreferencesRepository()
        val vm = OnboardingViewModel(prefs)
        compose.setContent { AiSkinTheme { OnboardingScreen(onFinished = {}, onOpenPrivacy = {}, vm = vm) } }

        repeat(3) { compose.onNodeWithText("Lanjut").performClick(); compose.waitForIdle() }
        compose.onNodeWithTag("onboarding_start").assertIsNotEnabled()
        compose.onNodeWithTag("consent_checkbox").performClick()
        compose.waitForIdle()
        assertTrue(prefs.state.value.privacyConsentAccepted)
        compose.onNodeWithTag("onboarding_start").assertIsEnabled()
    }

    @Test fun `error view shows retry and invokes callback`() {
        var retried = false
        compose.setContent { AiSkinTheme { ErrorView("Gagal", "Pesan galat", onRetry = { retried = true }) } }
        compose.onNodeWithText("Pesan galat").assertIsDisplayed()
        compose.onNodeWithText("Coba lagi").performClick()
        assertTrue(retried)
    }

    @Test fun `empty and loading states render`() {
        compose.setContent {
            AiSkinTheme {
                androidx.compose.foundation.layout.Column {
                    EmptyView("Belum ada riwayat", "Mulai pemindaian", actionLabel = "Mulai pindai", onAction = {})
                }
            }
        }
        compose.onNodeWithText("Belum ada riwayat").assertIsDisplayed()
        compose.onNodeWithText("Mulai pindai").assertIsDisplayed()
    }

    @Test fun `loading view shows message`() {
        compose.setContent { AiSkinTheme { LoadingView("Memuat riwayat...") } }
        compose.onNodeWithText("Memuat riwayat...").assertIsDisplayed()
    }
}
