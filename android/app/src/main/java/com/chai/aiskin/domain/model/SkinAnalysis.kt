package com.chai.aiskin.domain.model

import java.time.Instant

/**
 * Visual prominence on an ordinal scale, as approximately assessed by the AI.
 * NOT a clinical grade. [UNKNOWN] = could not be assessed from the photo.
 */
enum class Severity(val label: String, val level: Int?) {
    NONE("Tidak terlihat", 0),
    MILD("Ringan", 1),
    MODERATE("Sedang", 2),
    PRONOUNCED("Jelas terlihat", 3),
    UNKNOWN("Tidak dapat dinilai", null);

    companion object {
        fun from(raw: String?): Severity = when (raw) {
            "none" -> NONE
            "mild" -> MILD
            "moderate" -> MODERATE
            "pronounced" -> PRONOUNCED
            else -> UNKNOWN
        }
    }
}

enum class Texture(val label: String, val level: Int?) {
    SMOOTH("Halus", 0),
    SLIGHTLY_UNEVEN("Sedikit tidak rata", 1),
    UNEVEN("Tidak rata", 2),
    UNKNOWN("Tidak dapat dinilai", null);

    companion object {
        fun from(raw: String?) = when (raw) {
            "smooth" -> SMOOTH
            "slightly_uneven" -> SLIGHTLY_UNEVEN
            "uneven" -> UNEVEN
            else -> UNKNOWN
        }
    }
}

enum class ObservationCategory(val label: String) {
    PORES("Pori-pori"),
    ACNE_LIKE_LESIONS("Bintik menyerupai jerawat"),
    COMEDONES("Komedo"),
    TEXTURE("Tekstur"),
    PIGMENTATION("Pigmentasi"),
    REDNESS("Kemerahan"),
    DRYNESS_FLAKING("Kering / mengelupas"),
    VISIBLE_SHINE("Kilap"),
    OTHER("Lainnya");

    companion object {
        fun from(raw: String?) = when (raw) {
            "pores" -> PORES
            "acne_like_lesions" -> ACNE_LIKE_LESIONS
            "comedones" -> COMEDONES
            "texture" -> TEXTURE
            "pigmentation" -> PIGMENTATION
            "redness" -> REDNESS
            "dryness_flaking" -> DRYNESS_FLAKING
            "visible_shine" -> VISIBLE_SHINE
            else -> OTHER
        }
    }
}

enum class FaceArea(val label: String) {
    FOREHEAD("Dahi"), NOSE("Hidung"), CHEEKS("Pipi"), CHIN("Dagu"), JAWLINE("Rahang"),
    UNDER_EYE("Bawah mata"), T_ZONE("Area T"), FULL_FACE("Seluruh wajah"), UNSPECIFIED("Tidak spesifik");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNSPECIFIED
    }
}

enum class ImageQualityStatus(val label: String) {
    GOOD("Baik"), ACCEPTABLE("Cukup"), POOR("Kurang"), UNKNOWN("Tidak diketahui");

    companion object {
        fun from(raw: String?) = when (raw) {
            "good" -> GOOD
            "acceptable" -> ACCEPTABLE
            "poor" -> POOR
            else -> UNKNOWN
        }
    }
}

data class ImageQuality(
    val status: ImageQualityStatus,
    val lighting: String,
    val faceVisibility: String,
    val limitations: List<String>,
) {
    val lightingLabel: String
        get() = when (lighting) {
            "adequate" -> "Cukup"
            "too_dark" -> "Terlalu gelap"
            "too_bright" -> "Terlalu terang"
            "uneven" -> "Tidak merata"
            else -> "Tidak diketahui"
        }
    val faceVisibilityLabel: String
        get() = when (faceVisibility) {
            "clear" -> "Jelas"
            "partial" -> "Sebagian"
            "obstructed" -> "Terhalang"
            "unclear" -> "Kurang jelas"
            else -> "Tidak diketahui"
        }
}

data class Observation(
    val category: ObservationCategory,
    val severity: Severity,
    /** Approximate AI self-assessed confidence, 0..1. */
    val confidence: Double,
    val description: String,
    val area: FaceArea,
) {
    val confidenceLabel: String
        get() = when {
            confidence >= 0.75 -> "Keyakinan AI: cukup tinggi"
            confidence >= 0.5 -> "Keyakinan AI: sedang"
            else -> "Keyakinan AI: rendah"
        }
}

data class SkinAppearance(
    val texture: Texture,
    val redness: Severity,
    val dryness: Severity,
    val pigmentation: Severity,
    val shine: Severity,
)

enum class RecommendationPriority(val label: String) { HIGH("Prioritas"), MEDIUM("Disarankan"), LOW("Opsional") }

data class Recommendation(
    val category: String,
    val title: String,
    val description: String,
    val keyIngredients: List<String>,
    val routineTime: String,
    val priority: RecommendationPriority,
    val cautions: String,
) {
    val routineTimeLabel: String
        get() = when (routineTime) {
            "morning" -> "Pagi"
            "evening" -> "Malam"
            "both" -> "Pagi & malam"
            "weekly" -> "Mingguan"
            else -> "Bila perlu"
        }
}

data class SkinRoutine(val morning: List<String>, val evening: List<String>)

data class AnalysisFeedback(val helpful: Boolean, val accuracyRating: Int?, val comment: String)

data class SkinAnalysis(
    val id: String,
    val createdAt: Instant,
    val analysisVersion: String,
    val overallSummary: String,
    val imageQuality: ImageQuality,
    val observations: List<Observation>,
    val appearance: SkinAppearance,
    val recommendations: List<Recommendation>,
    val routine: SkinRoutine,
    val limitations: List<String>,
    val consultationRecommended: Boolean,
    val consultationReason: String,
    val photoStored: Boolean,
    val photoUrl: String?,
    val feedback: AnalysisFeedback?,
) {
    fun observationsFor(vararg categories: ObservationCategory) = observations.filter { it.category in categories }
}

data class HistoryItem(
    val id: String,
    val createdAt: Instant,
    val summary: String,
    val imageQuality: ImageQualityStatus,
    val categories: List<ObservationCategory>,
    val appearance: SkinAppearance,
    val consultationRecommended: Boolean,
    val photoStored: Boolean,
    val photoUrl: String? = null,
)

enum class ProductType(val label: String) {
    CLEANSER("Pembersih"), TONER("Toner"), SERUM("Serum"), MOISTURIZER("Pelembap"), SUNSCREEN("Tabir surya"),
    EXFOLIANT("Eksfoliasi"), SPOT_TREATMENT("Obat totol"), OTHER("Lainnya");

    companion object {
        fun from(raw: String) = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OTHER
    }
}

data class ProductSuggestion(
    val name: String,
    val brand: String,
    val type: ProductType,
    val keyIngredients: List<String>,
    val whyItFits: String,
    val howToUse: String,
    /** "Pagi", "Malam", "Pagi & malam" or "Mingguan". */
    val whenToUse: String,
    val priceRange: String,
    val cautions: String,
    val searchUrl: String,
    val shopUrl: String,
)

data class ProductSource(val title: String, val url: String)

data class ProductSuggestions(
    val summary: String,
    val products: List<ProductSuggestion>,
    val tips: List<String>,
    val sources: List<ProductSource>,
    /** false = Google Search was unavailable; answer is from AI knowledge only. */
    val searchGrounded: Boolean,
    val disclaimer: String,
    val createdAt: Instant,
)

data class HistoryPage(val items: List<HistoryItem>, val nextCursor: String?)
