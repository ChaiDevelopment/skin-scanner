package com.chai.aiskin.features

import com.chai.aiskin.features.auth.AuthValidation
import com.chai.aiskin.features.camera.CameraPermissionReducer
import com.chai.aiskin.features.camera.CameraPermissionStatus
import com.chai.aiskin.navigation.Route
import com.chai.aiskin.navigation.StartDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmallLogicTest {
    @Test fun `camera permission states`() {
        assertEquals(CameraPermissionStatus.GRANTED, CameraPermissionReducer.onResult(granted = true, shouldShowRationale = false))
        assertEquals(CameraPermissionStatus.DENIED, CameraPermissionReducer.onResult(granted = false, shouldShowRationale = true))
        assertEquals(CameraPermissionStatus.PERMANENTLY_DENIED, CameraPermissionReducer.onResult(granted = false, shouldShowRationale = false))
    }

    @Test fun `start destination prioritises camera after onboarding and sign-in`() {
        assertEquals(Route.Onboarding, StartDestination.resolve(onboardingDone = false, consentAccepted = false, signedIn = false, cameraGranted = false))
        assertEquals(Route.Onboarding, StartDestination.resolve(onboardingDone = true, consentAccepted = false, signedIn = true, cameraGranted = true))
        assertEquals(Route.Login, StartDestination.resolve(onboardingDone = true, consentAccepted = true, signedIn = false, cameraGranted = true))
        assertEquals(Route.Camera, StartDestination.resolve(onboardingDone = true, consentAccepted = true, signedIn = true, cameraGranted = true))
        assertEquals(Route.Home, StartDestination.resolve(onboardingDone = true, consentAccepted = true, signedIn = true, cameraGranted = false))
    }

    @Test fun `auth validation`() {
        assertNotNull(AuthValidation.email(""))
        assertNotNull(AuthValidation.email("no-at-sign"))
        assertNull(AuthValidation.email("user@example.com"))
        assertNotNull(AuthValidation.password("short1"))
        assertNotNull(AuthValidation.password("onlyletters"))
        assertNull(AuthValidation.password("goodpass123"))
    }
}
