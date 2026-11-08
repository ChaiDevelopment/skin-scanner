package com.chai.aiskin.features.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chai.aiskin.core.common.formatLongDate
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.AppCard
import com.chai.aiskin.core.designsystem.Eyebrow
import com.chai.aiskin.core.designsystem.HairlineDivider
import com.chai.aiskin.core.designsystem.LevelDots
import com.chai.aiskin.core.designsystem.NoticeBanner
import com.chai.aiskin.core.designsystem.SectionHeader
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.core.designsystem.StatusPill
import com.chai.aiskin.core.designsystem.Tone
import com.chai.aiskin.domain.model.ImageQualityStatus
import com.chai.aiskin.domain.model.Observation
import com.chai.aiskin.domain.model.ObservationCategory
import com.chai.aiskin.domain.model.Recommendation
import com.chai.aiskin.domain.model.RecommendationPriority
import com.chai.aiskin.domain.model.Severity
import com.chai.aiskin.domain.model.SkinAnalysis

/** Shared body for the fresh result screen and the history detail screen. */
@Composable
fun AnalysisBody(analysis: SkinAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Summary(analysis)
        if (analysis.consultationRecommended) {
            NoticeBanner(
                "Disarankan berkonsultasi dengan dokter spesialis kulit (Sp.DVE). " +
                    analysis.consultationReason.ifBlank { "Beberapa hal yang terlihat sebaiknya diperiksa langsung oleh tenaga profesional." },
                tone = Tone.Alert,
                icon = Icons.Outlined.LocalHospital,
            )
        }
        ImageQualityCard(analysis)
        AppearanceCard(analysis)
        ObservationsSection(analysis.observations)
        if (analysis.recommendations.isNotEmpty()) RecommendationsSection(analysis.recommendations)
        if (analysis.routine.morning.isNotEmpty() || analysis.routine.evening.isNotEmpty()) RoutineCard(analysis)
        LimitationsCard(analysis.limitations)
    }
}

@Composable
private fun Summary(a: SkinAnalysis) {
    Column {
        Eyebrow(a.createdAt.formatLongDate())
        Spacer(Modifier.height(Spacing.xs))
        Text("Ringkasan observasi", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(Spacing.sm))
        Text(a.overallSummary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImageQualityCard(a: SkinAnalysis) {
    val q = a.imageQuality
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kualitas foto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(q.status.label, when (q.status) {
                ImageQualityStatus.GOOD -> Tone.Positive
                ImageQualityStatus.ACCEPTABLE -> Tone.Caution
                else -> Tone.Alert
            })
        }
        Spacer(Modifier.height(Spacing.sm))
        KeyValue(Icons.Outlined.WbSunny, "Pencahayaan", q.lightingLabel)
        KeyValue(Icons.Outlined.DarkMode, "Visibilitas wajah", q.faceVisibilityLabel)
        q.limitations.forEach {
            Spacer(Modifier.height(Spacing.xxs))
            Text("•  $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KeyValue(icon: androidx.compose.ui.graphics.vector.ImageVector, k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AiSkinTheme.extended.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(v, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AppearanceCard(a: SkinAnalysis) {
    val ap = a.appearance
    val pores = strongest(a, ObservationCategory.PORES)
    val acne = strongest(a, ObservationCategory.ACNE_LIKE_LESIONS)
    val comedones = strongest(a, ObservationCategory.COMEDONES)
    AppCard {
        Text("Gambaran visual", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xxs))
        Text("Penilaian perkiraan AI dari foto ini — bukan pengukuran klinis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.md))
        LevelRow("Pori-pori terlihat", pores.label, pores.level)
        LevelRow("Bintik menyerupai jerawat", acne.label, acne.level)
        LevelRow("Komedo", comedones.label, comedones.level)
        LevelRow("Tekstur", ap.texture.label, ap.texture.level)
        LevelRow("Pigmentasi", ap.pigmentation.label, ap.pigmentation.level)
        LevelRow("Kemerahan", ap.redness.label, ap.redness.level)
        LevelRow("Kering / mengelupas", ap.dryness.label, ap.dryness.level)
        LevelRow("Kilap", ap.shine.label, ap.shine.level, last = true)
    }
}

private fun strongest(a: SkinAnalysis, c: ObservationCategory): Severity =
    a.observations.filter { it.category == c && it.severity != Severity.UNKNOWN }.maxByOrNull { it.severity.level ?: -1 }?.severity
        ?: Severity.UNKNOWN

@Composable
private fun LevelRow(label: String, value: String, level: Int?, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LevelDots(level)
    }
    if (!last) HairlineDivider()
}

@Composable
private fun ObservationsSection(observations: List<Observation>) {
    Column {
        SectionHeader("Yang terlihat di foto", eyebrow = "Observasi")
        Spacer(Modifier.height(Spacing.sm))
        if (observations.isEmpty()) {
            Text("Tidak ada observasi spesifik.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        observations.forEach { o ->
            AppCard(Modifier.padding(bottom = Spacing.sm), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.category.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    StatusPill(o.severity.label, Tone.Accent)
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(o.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                Text("${o.area.label}  ·  ${o.confidenceLabel}", style = MaterialTheme.typography.labelMedium, color = AiSkinTheme.extended.accent)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendationsSection(recs: List<Recommendation>) {
    Column {
        SectionHeader("Saran perawatan kulit", eyebrow = "Rekomendasi")
        Spacer(Modifier.height(Spacing.xxs))
        Text("Saran umum yang lembut, berdasarkan apa yang terlihat. Lakukan uji tempel (patch test) sebelum mencoba produk baru.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        recs.forEach { r ->
            AppCard(Modifier.padding(bottom = Spacing.sm), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    StatusPill(r.priority.label, if (r.priority == RecommendationPriority.HIGH) Tone.Accent else Tone.Neutral)
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(r.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (r.keyIngredients.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.keyIngredients.forEach { StatusPill(it, Tone.Neutral) }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text("Waktu: ${r.routineTimeLabel}", style = MaterialTheme.typography.labelMedium, color = AiSkinTheme.extended.accent)
                if (r.cautions.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Perhatian: ${r.cautions}", style = MaterialTheme.typography.bodySmall, color = AiSkinTheme.extended.caution)
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(a: SkinAnalysis) {
    AppCard {
        Text("Rutinitas sederhana", style = MaterialTheme.typography.titleMedium)
        if (a.routine.morning.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Eyebrow("Pagi")
            a.routine.morning.forEachIndexed { i, step -> Step(i + 1, step) }
        }
        if (a.routine.evening.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Eyebrow("Malam")
            a.routine.evening.forEachIndexed { i, step -> Step(i + 1, step) }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(top = Spacing.xs)) {
        Text("$n.", style = MaterialTheme.typography.bodyMedium, color = AiSkinTheme.extended.accent, modifier = Modifier.width(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LimitationsCard(limitations: List<String>) {
    AppCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = AiSkinTheme.extended.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("Keterbatasan analisis", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(Spacing.xs))
        (limitations.ifEmpty { listOf("Ini adalah observasi visual berbasis AI, bukan diagnosis medis.") }).forEach {
            Text("•  $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Konsultasikan ke dokter bila masalah kulit menetap, memburuk, nyeri, atau mengkhawatirkan.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
