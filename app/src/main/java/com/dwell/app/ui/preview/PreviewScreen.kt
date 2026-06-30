package com.dwell.app.ui.preview

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dwell.app.R
import com.dwell.app.data.model.Wallpaper
import com.dwell.app.data.wallpaper.WallpaperTarget

// A tablet gets the higher-resolution variant. 600dp is the standard split.
private const val TABLET_SMALLEST_WIDTH_DP = 600

@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP

    // Apply result fires a one-shot toast, then resets so it can't repeat on
    // recomposition. Copy rule: button "Apply" leads to toast "Applied."
    LaunchedEffect(state.applyState) {
        val message = when (state.applyState) {
            ApplyState.Applied -> R.string.preview_applied
            ApplyState.Failed -> R.string.preview_apply_failed
            else -> null
        }
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeApplyResult()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        val wallpaper = state.wallpaper
        when {
            wallpaper != null -> {
                AsyncImage(
                    model = wallpaper.fullUrl(isTablet),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                ApplyPanel(
                    wallpaper = wallpaper,
                    target = state.target,
                    isApplying = state.applyState == ApplyState.Applying,
                    isFavorite = state.isFavorite,
                    onSelectTarget = viewModel::selectTarget,
                    onApply = { viewModel.apply(isTablet) },
                    onToggleFavorite = viewModel::onToggleFavorite,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            state.notFound -> NotFound(modifier = Modifier.align(Alignment.Center))
            // While loading there is just the scrim; the cache read is instant.
        }

        BackButton(
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(4.dp),
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.cd_back),
            tint = Color.White,
        )
    }
}

@Composable
private fun NotFound(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.preview_not_found),
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(32.dp),
    )
}

@Composable
private fun ApplyPanel(
    wallpaper: Wallpaper,
    target: WallpaperTarget,
    isApplying: Boolean,
    isFavorite: Boolean,
    onSelectTarget: (WallpaperTarget) -> Unit,
    onApply: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 20.dp),
        ) {
            DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(14.dp))
            TitleRow(
                wallpaper = wallpaper,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
            )
            Spacer(Modifier.height(16.dp))
            TargetSelector(selected = target, onSelect = onSelectTarget)
            Spacer(Modifier.height(12.dp))
            ApplyButton(isApplying = isApplying, onApply = onApply)
        }
    }
}

@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(32.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun TitleRow(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = wallpaper.title ?: stringResource(R.string.preview_untitled),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = metaLine(wallpaper),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .size(44.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        ) {
            Icon(
                painter = painterResource(
                    if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                ),
                contentDescription = stringResource(R.string.cd_favorite),
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun metaLine(wallpaper: Wallpaper): String {
    val source = if (wallpaper.isAiGenerated) {
        stringResource(R.string.preview_meta_ai)
    } else {
        stringResource(R.string.preview_meta_designed)
    }
    return "${wallpaper.category} · $source".uppercase()
}

@Composable
private fun TargetSelector(
    selected: WallpaperTarget,
    onSelect: (WallpaperTarget) -> Unit,
) {
    val options = listOf(
        WallpaperTarget.HOME to R.string.preview_target_home,
        WallpaperTarget.LOCK to R.string.preview_target_lock,
        WallpaperTarget.BOTH to R.string.preview_target_both,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { (option, labelRes) ->
            val isSelected = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onSelect(option) },
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ApplyButton(isApplying: Boolean, onApply: () -> Unit) {
    Button(
        onClick = onApply,
        enabled = !isApplying,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (isApplying) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.preview_apply),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
