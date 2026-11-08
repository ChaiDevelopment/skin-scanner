package com.chai.aiskin.data.model

import com.chai.aiskin.data.remote.dto.AnalysisDto
import com.chai.aiskin.data.remote.dto.HistoryItemDto
import com.chai.aiskin.data.remote.dto.HistoryResponseDto
import com.chai.aiskin.data.remote.dto.ImageQualityDto
import com.chai.aiskin.data.remote.dto.SkinAppearanceDto
import com.chai.aiskin.domain.model.AnalysisFeedback
import com.chai.aiskin.domain.model.FaceArea
import com.chai.aiskin.domain.model.HistoryItem
import com.chai.aiskin.domain.model.HistoryPage
import com.chai.aiskin.domain.model.ImageQuality
import com.chai.aiskin.domain.model.ImageQualityStatus
import com.chai.aiskin.domain.model.Observation
import com.chai.aiskin.domain.model.ObservationCategory
import com.chai.aiskin.domain.model.Recommendation
import com.chai.aiskin.domain.model.RecommendationPriority
import com.chai.aiskin.domain.model.Severity
import com.chai.aiskin.domain.model.SkinAnalysis
import com.chai.aiskin.domain.model.SkinAppearance
import com.chai.aiskin.domain.model.SkinRoutine
import com.chai.aiskin.domain.model.Texture
import java.time.Instant

private fun parseInstant(raw: String): Instant = runCatching { Instant.parse(raw) }.getOrDefault(Instant.EPOCH)

fun ImageQualityDto.toDomain() = ImageQuality(
    status = ImageQualityStatus.from(status),
    lighting = lighting,
    faceVisibility = faceVisibility,
    limitations = limitations,
)

fun SkinAppearanceDto.toDomain() = SkinAppearance(
    texture = Texture.from(texture),
    redness = Severity.from(visibleRedness),
    dryness = Severity.from(visibleDryness),
    pigmentation = Severity.from(pigmentation),
    shine = Severity.from(visibleShine),
)

fun AnalysisDto.toDomain() = SkinAnalysis(
    id = analysisId,
    createdAt = parseInstant(createdAt),
    analysisVersion = analysisVersion,
    overallSummary = overallSummary,
    imageQuality = imageQuality.toDomain(),
    observations = observations.map {
        Observation(
            category = ObservationCategory.from(it.category),
            severity = Severity.from(it.severity),
            confidence = it.confidence.coerceIn(0.0, 1.0),
            description = it.observation,
            area = FaceArea.from(it.affectedArea),
        )
    },
    appearance = skinAppearance.toDomain(),
    recommendations = recommendations.map {
        Recommendation(
            category = it.category,
            title = it.title,
            description = it.description,
            keyIngredients = it.keyIngredients,
            routineTime = it.routineTime,
            priority = when (it.priority) {
                "high" -> RecommendationPriority.HIGH
                "low" -> RecommendationPriority.LOW
                else -> RecommendationPriority.MEDIUM
            },
            cautions = it.cautions,
        )
    }.sortedBy { it.priority.ordinal },
    routine = SkinRoutine(routine.morning, routine.evening),
    limitations = limitations,
    consultationRecommended = professionalConsultationRecommended,
    consultationReason = professionalConsultationReason,
    photoStored = photoStorageEnabled,
    photoUrl = photoUrl,
    feedback = feedback?.let { AnalysisFeedback(it.helpful, it.accuracyRating, it.comment) },
)

fun HistoryItemDto.toDomain() = HistoryItem(
    id = analysisId,
    createdAt = parseInstant(createdAt),
    summary = overallSummary,
    imageQuality = ImageQualityStatus.from(imageQualityStatus),
    categories = observationCategories.map(ObservationCategory::from).distinct(),
    appearance = skinAppearance.toDomain(),
    consultationRecommended = professionalConsultationRecommended,
    photoStored = photoStorageEnabled,
    photoUrl = photoUrl,
)

fun HistoryResponseDto.toDomain() = HistoryPage(items.map { it.toDomain() }, nextCursor)
