package com.chai.aiskin.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Mirrors the backend controller responses (backend/src/controllers). Enum-like fields stay strings and are mapped defensively. */

@Serializable
data class ApiEnvelope<T>(val success: Boolean, val data: T? = null, val error: ApiErrorDto? = null, val requestId: String? = null)

@Serializable
data class ApiErrorDto(val code: String = "UNKNOWN", val message: String = "", val details: JsonElement? = null)

@Serializable
data class ApiErrorEnvelope(val success: Boolean = false, val error: ApiErrorDto? = null, val requestId: String? = null)

@Serializable
data class InsufficientQualityDetailsDto(val imageQuality: ImageQualityDto? = null, val limitations: List<String> = emptyList())

@Serializable
data class ImageQualityDto(
    val status: String = "unknown",
    val lighting: String = "unknown",
    val faceVisibility: String = "unknown",
    val limitations: List<String> = emptyList(),
)

@Serializable
data class ObservationDto(
    val category: String,
    val severity: String,
    val confidence: Double = 0.0,
    val observation: String,
    val affectedArea: String = "unspecified",
)

@Serializable
data class SkinAppearanceDto(
    val texture: String = "unknown",
    val visibleRedness: String = "unknown",
    val visibleDryness: String = "unknown",
    val pigmentation: String = "unknown",
    val visibleShine: String = "unknown",
)

@Serializable
data class RecommendationDto(
    val category: String,
    val title: String,
    val description: String,
    val keyIngredients: List<String> = emptyList(),
    val routineTime: String = "as_needed",
    val priority: String = "medium",
    val cautions: String = "",
)

@Serializable
data class RoutineDto(val morning: List<String> = emptyList(), val evening: List<String> = emptyList())

@Serializable
data class FeedbackDto(val helpful: Boolean, val accuracyRating: Int? = null, val comment: String = "", val createdAt: String? = null)

@Serializable
data class AnalysisDto(
    val analysisId: String,
    val createdAt: String,
    val status: String,
    val analysisVersion: String = "1.0",
    val overallSummary: String,
    val imageQuality: ImageQualityDto,
    val observations: List<ObservationDto> = emptyList(),
    val skinAppearance: SkinAppearanceDto = SkinAppearanceDto(),
    val recommendations: List<RecommendationDto> = emptyList(),
    val routine: RoutineDto = RoutineDto(),
    val limitations: List<String> = emptyList(),
    val professionalConsultationRecommended: Boolean = false,
    val professionalConsultationReason: String = "",
    val photoStorageEnabled: Boolean = false,
    val photoUrl: String? = null,
    val feedback: FeedbackDto? = null,
)

@Serializable
data class AnalyzeResponseDto(val analysisId: String, val status: String, val analysis: AnalysisDto)

@Serializable
data class HistoryItemDto(
    val analysisId: String,
    val createdAt: String,
    val status: String,
    val overallSummary: String,
    val imageQualityStatus: String = "unknown",
    val observationCategories: List<String> = emptyList(),
    val skinAppearance: SkinAppearanceDto = SkinAppearanceDto(),
    val professionalConsultationRecommended: Boolean = false,
    val photoStorageEnabled: Boolean = false,
)

@Serializable
data class HistoryResponseDto(val items: List<HistoryItemDto>, val nextCursor: String? = null)

@Serializable
data class FeedbackRequestDto(val helpful: Boolean, val accuracyRating: Int? = null, val comment: String? = null)

@Serializable
data class AccountDeletionRequestDto(val confirm: String = "DELETE")

@Serializable
data class GenericAckDto(val analysisId: String? = null, val deleted: Boolean? = null)
