package com.chai.aiskin.domain.usecase

import com.chai.aiskin.domain.model.ObservationCategory
import com.chai.aiskin.domain.model.Severity
import com.chai.aiskin.domain.model.SkinAnalysis
import javax.inject.Inject

enum class VisualChange(val label: String) {
    LESS_VISIBLE("Tampak lebih ringan"),
    SIMILAR("Tampak serupa"),
    MORE_VISIBLE("Tampak lebih jelas"),
    NOT_COMPARABLE("Tidak dapat dibandingkan"),
}

data class ComparisonRow(
    val label: String,
    val olderLabel: String,
    val newerLabel: String,
    val olderLevel: Int?,
    val newerLevel: Int?,
    val change: VisualChange,
)

data class Comparison(
    val older: SkinAnalysis,
    val newer: SkinAnalysis,
    val rows: List<ComparisonRow>,
    val disclaimer: String,
    val qualityWarning: String?,
)

/**
 * Compares two analyses attribute by attribute on the ordinal visual scale.
 * Never aggregates into a single score and never calls a change "improvement".
 */
class CompareAnalysesUseCase @Inject constructor() {

    operator fun invoke(a: SkinAnalysis, b: SkinAnalysis): Comparison {
        val (older, newer) = if (a.createdAt <= b.createdAt) a to b else b to a
        val rows = buildList {
            add(row("Tekstur", older.appearance.texture.label, newer.appearance.texture.label, older.appearance.texture.level, newer.appearance.texture.level))
            add(severityRow("Kemerahan", older.appearance.redness, newer.appearance.redness))
            add(severityRow("Pigmentasi", older.appearance.pigmentation, newer.appearance.pigmentation))
            add(severityRow("Kering / mengelupas", older.appearance.dryness, newer.appearance.dryness))
            add(severityRow("Kilap", older.appearance.shine, newer.appearance.shine))
            for (cat in listOf(ObservationCategory.PORES, ObservationCategory.ACNE_LIKE_LESIONS, ObservationCategory.COMEDONES)) {
                add(severityRow(cat.label, strongest(older, cat), strongest(newer, cat)))
            }
        }
        val poorQuality = listOf(older, newer).any { it.imageQuality.status.name in setOf("POOR", "UNKNOWN") }
        return Comparison(
            older = older,
            newer = newer,
            rows = rows,
            disclaimer = DISCLAIMER,
            qualityWarning = if (poorQuality) "Salah satu foto memiliki kualitas kurang baik, sehingga perbandingan kurang dapat diandalkan." else null,
        )
    }

    /** Category not mentioned at all → UNKNOWN (absence of a mention is not evidence of "none"). */
    private fun strongest(a: SkinAnalysis, cat: ObservationCategory): Severity =
        a.observations.filter { it.category == cat && it.severity != Severity.UNKNOWN }
            .maxByOrNull { it.severity.level ?: -1 }?.severity ?: Severity.UNKNOWN

    private fun severityRow(label: String, o: Severity, n: Severity) = row(label, o.label, n.label, o.level, n.level)

    private fun row(label: String, oLabel: String, nLabel: String, o: Int?, n: Int?) = ComparisonRow(
        label = label,
        olderLabel = oLabel,
        newerLabel = nLabel,
        olderLevel = o,
        newerLevel = n,
        change = when {
            o == null || n == null -> VisualChange.NOT_COMPARABLE
            n < o -> VisualChange.LESS_VISIBLE
            n > o -> VisualChange.MORE_VISIBLE
            else -> VisualChange.SIMILAR
        },
    )

    companion object {
        const val DISCLAIMER =
            "Perbedaan antar foto dapat disebabkan oleh pencahayaan, pengaturan kamera, dan posisi wajah. " +
                "Perubahan yang tampak bukan bukti perbaikan atau perburukan klinis."
    }
}
