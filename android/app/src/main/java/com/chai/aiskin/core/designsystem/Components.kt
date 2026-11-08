package com.chai.aiskin.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(Spacing.buttonHeight),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(Spacing.buttonHeight),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = AiSkinTheme.extended.accent) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Soft card with hairline border; the base surface for most content blocks. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = AiSkinTheme.extended.hairline,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) { Column(Modifier.padding(contentPadding), content = content) }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) { Column(Modifier.padding(contentPadding), content = content) }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = AiSkinTheme.extended.accent) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, eyebrow: String? = null, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Eyebrow(eyebrow)
                Spacer(Modifier.height(Spacing.xxs))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        action?.invoke(this)
    }
}

enum class Tone { Neutral, Positive, Caution, Alert, Accent }

@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val ext = AiSkinTheme.extended
    val (bg, fg) = when (tone) {
        Tone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        Tone.Positive -> ext.positiveSoft to ext.positive
        Tone.Caution -> ext.cautionSoft to ext.caution
        Tone.Alert -> ext.alertSoft to ext.alert
        Tone.Accent -> ext.accentSoft to MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier
            .background(bg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** Four-step ordinal indicator (none → pronounced). Unknown shows as hollow. Purely visual, not a measurement. */
@Composable
fun LevelDots(level: Int?, modifier: Modifier = Modifier, tone: Tone = Tone.Accent) {
    val ext = AiSkinTheme.extended
    val active = when (tone) {
        Tone.Positive -> ext.positive
        Tone.Caution -> ext.caution
        Tone.Alert -> ext.alert
        else -> ext.accent
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val filled = level != null && i < level
            Box(
                Modifier
                    .size(width = 18.dp, height = 6.dp)
                    .background(if (filled) active else Color.Transparent, CircleShape)
                    .border(1.dp, if (level == null) MaterialTheme.colorScheme.outline else if (filled) active else MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = AiSkinTheme.extended.hairline)
}

@Composable
fun LoadingView(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = AiSkinTheme.extended.accent, strokeWidth = 2.dp)
        Spacer(Modifier.height(Spacing.md))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorView(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String = "Coba lagi",
    onRetry: (() -> Unit)? = null,
    icon: ImageVector = Icons.Outlined.CloudOff,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    MessageView(icon, title, message, modifier, AiSkinTheme.extended.alert) {
        if (onRetry != null) PrimaryButton(retryLabel, onRetry)
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(Spacing.sm))
            SecondaryButton(secondaryLabel, onSecondary)
        }
    }
}

@Composable
fun EmptyView(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    MessageView(icon, title, message, modifier, AiSkinTheme.extended.accent) {
        if (actionLabel != null && onAction != null) PrimaryButton(actionLabel, onAction)
    }
}

@Composable
private fun MessageView(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier,
    iconTint: Color,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).background(AiSkinTheme.extended.accentSoft.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(Spacing.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.lg))
        actions()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.semantics { role = Role.Button }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
fun NoticeBanner(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Neutral, icon: ImageVector? = null) {
    val ext = AiSkinTheme.extended
    val (bg, fg) = when (tone) {
        Tone.Alert -> ext.alertSoft to ext.alert
        Tone.Caution -> ext.cautionSoft to MaterialTheme.colorScheme.onSurface
        Tone.Positive -> ext.positiveSoft to MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier.fillMaxWidth().background(bg, MaterialTheme.shapes.medium).padding(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (tone == Tone.Alert) ext.alert else ext.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg)
    }
}
