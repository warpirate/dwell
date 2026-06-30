package com.dwell.app.ui.preview

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dwell.app.R
import com.dwell.app.data.model.Wallpaper
import com.dwell.app.data.wallpaper.WallpaperTarget
import com.dwell.app.ui.components.DwellPrimaryButton
import com.dwell.app.ui.components.DwellSegmentedToggle
import com.dwell.app.ui.theme.DwellSheetShape
import com.dwell.app.ui.theme.DwellSpacing
import com.dwell.app.ui.theme.dwellSoftShadow
import com.dwell.app.ui.theme.topScrim

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
                // Warm top scrim so the white back control reads on light wallpapers.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(topScrim()),
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
        shape = DwellSheetShape,
        modifier = modifier
            .fillMaxWidth()
            .dwellSoftShadow(DwellSheetShape),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = DwellSpacing.xl)
                .padding(top = DwellSpacing.sm + 2.dp, bottom = DwellSpacing.xl),
        ) {
            DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(DwellSpacing.md + 2.dp))
            TitleRow(
                wallpaper = wallpaper,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
            )
            Spacer(Modifier.height(DwellSpacing.lg))
            DwellSegmentedToggle(
                options = listOf(
                    WallpaperTarget.HOME to stringResource(R.string.preview_target_home),
                    WallpaperTarget.LOCK to stringResource(R.string.preview_target_lock),
                    WallpaperTarget.BOTH to stringResource(R.string.preview_target_both),
                ),
                selected = target,
                onSelect = onSelectTarget,
            )
            Spacer(Modifier.height(DwellSpacing.md))
            DwellPrimaryButton(
                text = stringResource(R.string.preview_apply),
                onClick = onApply,
                loading = isApplying,
            )
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
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
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

