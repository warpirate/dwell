package com.dwell.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dwell.app.R

/**
 * The shared heart affordance: filled + accent tint when active, outline +
 * quiet tint otherwise (accent as a mark, no box). 48dp touch target.
 */
@Composable
fun FavoriteToggle(
    favorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier.size(48.dp)) {
        Icon(
            painter = painterResource(if (favorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline),
            contentDescription = stringResource(R.string.cd_favorite),
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
