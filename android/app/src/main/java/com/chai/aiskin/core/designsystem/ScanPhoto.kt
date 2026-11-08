package com.chai.aiskin.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * A stored scan photo loaded from a short-lived signed URL.
 * Shows a soft placeholder while loading and an icon if the URL is missing, expired or unreachable.
 */
@Composable
fun ScanPhoto(
    url: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    contentDescription: String? = "Foto pemindaian",
) {
    val ext = AiSkinTheme.extended
    var failed by remember(url) { mutableStateOf(url == null) }
    Box(modifier.clip(shape).background(ext.accentSoft), contentAlignment = Alignment.Center) {
        if (failed) {
            Icon(
                if (url == null) Icons.Outlined.Face else Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = ext.accent,
                modifier = Modifier.size(iconSize),
            )
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onState = { failed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
