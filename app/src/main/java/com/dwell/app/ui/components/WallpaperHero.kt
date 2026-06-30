package com.dwell.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.dwell.app.ui.theme.warmScrimVertical

/**
 * Full-bleed wallpaper behind an eased warm scrim, with a content slot for chrome
 * in the readable (scrimmed) zone. The product as the backdrop — the auth hero
 * and (later) the preview both sit on this. The scrim guarantees text contrast
 * over any wallpaper.
 */
@Composable
fun WallpaperHero(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    scrim: Boolean = true,
    kenBurns: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val scale = if (kenBurns) {
        val transition = rememberInfiniteTransition(label = "kenBurns")
        val s by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
            label = "kenBurnsScale",
        )
        s
    } else {
        1.0f
    }

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale },
        )
        if (scrim) {
            Box(modifier = Modifier.fillMaxSize().background(warmScrimVertical()))
        }
        content()
    }
}
