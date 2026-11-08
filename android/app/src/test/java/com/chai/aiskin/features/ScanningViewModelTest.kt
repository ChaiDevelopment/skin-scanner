package com.chai.aiskin.features

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chai.aiskin.FakeAnalysisRepository
import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.detection.FaceValidation
import com.chai.aiskin.domain.usecase.AnalyzeSkinUseCase
import com.chai.aiskin.features.scanning.ScanningUiState
import com.chai.aiskin.features.scanning.ScanningViewModel
import com.chai.aiskin.scanner.CapturedImageProcessor
import com.chai.aiskin.scanner.CapturedScan
import com.chai.aiskin.scanner.ScanSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ScanningViewModelTest {
    private lateinit var session: ScanSession
    private lateinit var repo: FakeAnalysisRepository
    private lateinit var photo: File

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val processor = CapturedImageProcessor(ctx)
        session = ScanSession(processor)
        repo = FakeAnalysisRepository()
        photo = File(ctx.cacheDir, "scans").apply { mkdirs() }.resolve("scan_test.jpg").apply { writeBytes(ByteArray(2048) { 1 }) }
        session.setCapture(CapturedScan(photo, Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), FaceValidation(emptyList(), null)))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `success navigates to result and clears the local photo`() {
        val vm = ScanningViewModel(AnalyzeSkinUseCase(repo), session)
        assertTrue(vm.state.value is ScanningUiState.Done)
        assertEquals(1, repo.analyzeCalls)
        assertNull(session.capture.value)
        assertFalse(photo.exists())
        assertEquals("a1", session.lastResult?.id)
    }

    @Test fun `failure keeps photo for retry, retry re-submits`() {
        repo.analyzeResult = AppResult.Failure(AppError.Network())
        val vm = ScanningViewModel(AnalyzeSkinUseCase(repo), session)
        val s = vm.state.value
        assertTrue(s is ScanningUiState.Failed && s.error is AppError.Network)
        assertTrue(photo.exists())
        repo.analyzeResult = AppResult.Success(com.chai.aiskin.TestFixtures.analysis("a2"))
        vm.start()
        assertEquals(2, repo.analyzeCalls)
        assertEquals(ScanningUiState.Done("a2"), vm.state.value)
    }

    @Test fun `insufficient quality surfaces as its own state`() {
        repo.analyzeResult = AppResult.Failure(AppError.InsufficientQuality(hints = listOf("Terlalu gelap")))
        val vm = ScanningViewModel(AnalyzeSkinUseCase(repo), session)
        val s = vm.state.value as ScanningUiState.Failed
        assertTrue(s.error is AppError.InsufficientQuality)
    }

    @Test fun `no photo in session`() {
        session.clearCapture()
        val vm = ScanningViewModel(AnalyzeSkinUseCase(repo), session)
        assertEquals(ScanningUiState.NoPhoto, vm.state.value)
        assertEquals(0, repo.analyzeCalls)
    }
}
