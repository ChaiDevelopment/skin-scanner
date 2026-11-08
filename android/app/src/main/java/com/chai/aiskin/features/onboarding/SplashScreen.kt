package com.chai.aiskin.features.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.core.designsystem.Eyebrow
import com.chai.aiskin.core.designsystem.Spacing
import com.chai.aiskin.navigation.Route

@Composable
fun SplashScreen(startDestination: Route?, onResolved: (Route) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(600), label = "splash")
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(startDestination) {
        if (startDestination != null) {
            kotlinx.coroutines.delay(700)
            onResolved(startDestination)
        }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(Modifier.size(96.dp))
        Spacer(Modifier.height(Spacing.lg))
        Text("AI Skin Scanner", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(Spacing.xs))
        Eyebrow("Observasi kulit visual")
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    val accent = AiSkinTheme.extended.accent
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = s * 0.03f
        drawCircle(accent, radius = s * 0.44f, style = Stroke(stroke))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.40f, s * 0.30f)
            cubicTo(s * 0.32f, s * 0.42f, s * 0.33f, s * 0.60f, s * 0.43f, s * 0.68f)
            cubicTo(s * 0.49f, s * 0.73f, s * 0.57f, s * 0.73f, s * 0.62f, s * 0.67f)
        }
        drawPath(path, ink, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(accent, radius = s * 0.035f, center = Offset(s * 0.63f, s * 0.36f))
    }
}
