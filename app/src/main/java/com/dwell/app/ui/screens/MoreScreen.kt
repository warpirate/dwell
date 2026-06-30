package com.dwell.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dwell.app.R
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.components.SettingsRow
import com.dwell.app.ui.theme.DwellSpacing

@Composable
fun MoreScreen(
    isSignedIn: Boolean,
    email: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DwellScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSignedIn) {
                AccountRow(email = email.orEmpty(), onSignOut = onSignOut)
            } else {
                SettingsRow(
                    title = stringResource(R.string.account_sign_in),
                    onClick = onSignIn,
                    leadingIcon = painterResource(R.drawable.ic_heart_outline),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingsRow(
                title = stringResource(R.string.favorites_title),
                onClick = onOpenFavorites,
                leadingIcon = painterResource(R.drawable.ic_heart_outline),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun AccountRow(email: String, onSignOut: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = DwellSpacing.screenGutter, vertical = DwellSpacing.md + 2.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.account_synced),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.account_sign_out),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onSignOut)
                .padding(DwellSpacing.sm),
        )
    }
}
