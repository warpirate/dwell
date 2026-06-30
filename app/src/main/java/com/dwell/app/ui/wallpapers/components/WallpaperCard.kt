package com.dwell.app.ui.wallpapers.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dwell.app.R
import com.dwell.app.data.model.Wallpaper
import com.dwell.app.ui.theme.AccentDark

/**
 * One grid thumbnail. The card sits in the wallpaper's dominant color so the
 * cell has a calm placeholder while the image streams in (no gray flash, no
 * layout jump). Schema has no dimensions yet, so a stable per-id aspect ratio
 * gives the grid its staggered shape. Flagged: derived, swap for real
 * dimensions when the schema carries them.
 */
@Composable
fun WallpaperCard(
    wallpaper: Wallpaper,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val aspect = remember(wallpaper.id) { aspectFor(wallpaper.id) }
    val placeholderColor = remember(wallpaper.dominantColor) {
        parseColorOrNull(wallpaper.dominantColor) ?: FALLBACK_PLACEHOLDER
    }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = placeholderColor,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = wallpaper.thumbUrl,
                contentDescription = wallpaper.title ?: stringResource(R.string.cd_wallpaper),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (onToggleFavorite != null) {
                CardFavoriteHeart(favorite = favorite, onToggle = onToggleFavorite)
            }
        }
    }
}

/**
 * The per-card save affordance: a heart over a constant dark scrim chip so it
 * reads on light *and* dark imagery. White outline when unsaved; saved fills
 * with [AccentDark] — the lighter sage that stays legible on the dark chip in
 * both app themes (the darker AccentLight green would vanish here). Accent as a
 * state mark. Its own click swallows the card tap, so the heart never opens the
 * preview.
 */
@Composable
private fun BoxScope.CardFavoriteHeart(favorite: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.40f))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                if (favorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
            ),
            contentDescription = stringResource(R.string.cd_favorite),
            tint = if (favorite) AccentDark else Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

// Aspect = width / height. Values < 1 are portrait (taller than wide), which
// suits wallpapers. Chosen by a stable hash of the id so layout never jumps.
private val ASPECT_RATIOS = listOf(0.62f, 0.70f, 0.78f, 0.66f, 0.74f)

private fun aspectFor(id: String): Float =
    ASPECT_RATIOS[(id.hashCode() and Int.MAX_VALUE) % ASPECT_RATIOS.size]

private val FALLBACK_PLACEHOLDER = Color(0xFF1A1A18)

private fun parseColorOrNull(hex: String): Color? = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: IllegalArgumentException) {
    null
}
