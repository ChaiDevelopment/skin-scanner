package com.chai.aiskin

import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.domain.model.FaceArea
import com.chai.aiskin.domain.model.HistoryItem
import com.chai.aiskin.domain.model.HistoryPage
import com.chai.aiskin.domain.model.ProductSuggestions
import com.chai.aiskin.domain.model.ImageQuality
import com.chai.aiskin.domain.model.ImageQualityStatus
import com.chai.aiskin.domain.model.Observation
import com.chai.aiskin.domain.model.ObservationCategory
import com.chai.aiskin.domain.model.Severity
import com.chai.aiskin.domain.model.SkinAnalysis
import com.chai.aiskin.domain.model.SkinAppearance
import com.chai.aiskin.domain.model.SkinRoutine
import com.chai.aiskin.domain.model.Texture
import com.chai.aiskin.domain.model.UserPreferences
import com.chai.aiskin.domain.repository.AnalysisRepository
import com.chai.aiskin.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.Instant

/** TEST-ONLY fixtures. Never used by production code. */
object TestFixtures {
    fun analysis(
        id: String = "a1",
        createdAt: Instant = Instant.parse("2026-01-01T10:00:00Z"),
        texture: Texture = Texture.SLIGHTLY_UNEVEN,
        redness: Severity = Severity.MILD,
        pores: Severity = Severity.MODERATE,
        quality: ImageQualityStatus = ImageQualityStatus.GOOD,
    ) = SkinAnalysis(
        id = id, createdAt = createdAt, analysisVersion = "1.0", overallSummary = "Ringkasan uji",
        imageQuality = ImageQuality(quality, "adequate", "clear", emptyList()),
        observations = listOf(Observation(ObservationCategory.PORES, pores, 0.8, "Pori terlihat", FaceArea.CHEEKS)),
        appearance = SkinAppearance(texture, redness, Severity.UNKNOWN, Severity.MILD, Severity.NONE),
        recommendations = emptyList(), routine = SkinRoutine(emptyList(), emptyList()), limitations = emptyList(),
        consultationRecommended = false, consultationReason = "", photoStored = false, photoUrl = null, feedback = null,
    )

    fun historyItem(id: String) = HistoryItem(
        id = id, createdAt = Instant.parse("2026-01-01T10:00:00Z"), summary = "s$id", imageQuality = ImageQualityStatus.GOOD,
        categories = listOf(ObservationCategory.PORES),
        appearance = SkinAppearance(Texture.SMOOTH, Severity.NONE, Severity.NONE, Severity.NONE, Severity.NONE),
        consultationRecommended = false, photoStored = false,
    )
}

class FakeAnalysisRepository(totalItems: Int = 0) : AnalysisRepository {
    val items = (1..totalItems).map { TestFixtures.historyItem("id$it") }.toMutableList()
    var historyCalls = mutableListOf<String?>()
    var failHistory: AppError? = null
    var failDelete: AppError? = null
    var analyzeResult: AppResult<SkinAnalysis> = AppResult.Success(TestFixtures.analysis())
    var analyzeCalls = 0

    override suspend fun analyze(image: File, storePhoto: Boolean): AppResult<SkinAnalysis> { analyzeCalls++; return analyzeResult }

    override suspend fun history(cursor: String?, limit: Int): AppResult<HistoryPage> {
        historyCalls += cursor
        failHistory?.let { return AppResult.Failure(it) }
        val start = cursor?.let { c -> items.indexOfFirst { it.id == c } + 1 } ?: 0
        val page = items.drop(start).take(limit)
        val next = if (start + limit < items.size) page.last().id else null
        return AppResult.Success(HistoryPage(page, next))
    }

    override suspend fun get(id: String) = AppResult.Success(TestFixtures.analysis(id))
    var productsResult: AppResult<ProductSuggestions> = AppResult.Failure(AppError.Server("not stubbed"))
    val productCalls = mutableListOf<Pair<String, Boolean>>()
    override suspend fun findProducts(id: String, refresh: Boolean): AppResult<ProductSuggestions> {
        productCalls += id to refresh
        return productsResult
    }
    override suspend fun delete(id: String): AppResult<Unit> {
        failDelete?.let { return AppResult.Failure(it) }
        items.removeAll { it.id == id }
        return AppResult.Success(Unit)
    }
    override suspend fun deletePhoto(id: String) = AppResult.Success(Unit)
    override suspend fun sendFeedback(id: String, helpful: Boolean, rating: Int?, comment: String?) = AppResult.Success(Unit)
    override suspend fun deleteAccount() = AppResult.Success(Unit)
}

class FakePreferencesRepository(initial: UserPreferences = UserPreferences()) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences = state
    override suspend fun setOnboardingCompleted(done: Boolean) = state.update { it.copy(onboardingCompleted = done) }
    override suspend fun setPrivacyConsent(accepted: Boolean) = state.update { it.copy(privacyConsentAccepted = accepted) }
    override suspend fun setStorePhotos(enabled: Boolean) = state.update { it.copy(storePhotos = enabled) }
    override suspend fun setDevMode(enabled: Boolean) = state.update { it.copy(devModeEnabled = enabled) }
    override suspend fun clearAccountScoped() = state.update { it.copy(storePhotos = false, devModeEnabled = false) }
}
