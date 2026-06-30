package com.dwell.app.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dwell.app.R
import com.dwell.app.data.auth.AuthError
import com.dwell.app.ui.components.DwellCard
import com.dwell.app.ui.components.DwellDisplayTitle
import com.dwell.app.ui.components.DwellPrimaryButton
import com.dwell.app.ui.components.DwellSecondaryButton
import com.dwell.app.ui.components.DwellSegmentedToggle
import com.dwell.app.ui.components.DwellTextField
import com.dwell.app.ui.components.SectionLabel
import com.dwell.app.ui.components.WallpaperHero
import com.dwell.app.ui.theme.DwellSpacing
import com.dwell.app.ui.theme.DwellTheme
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hero by viewModel.heroUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEmailForm by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) {
        if (state.done) {
            if (state.mergedExisting) {
                Toast.makeText(context, R.string.account_merged_toast, Toast.LENGTH_LONG).show()
            }
            onSignedIn()
        }
    }

    // Content sits over a warm dark scrim, so chrome is always dark-styled
    // (cream text, light outlines) regardless of the app's light/dark setting.
    DwellTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            WallpaperHero(
                model = hero,
                contentDescription = null,
                kenBurns = true,
            ) {
                // Skip — Dwell has no login wall.
                Text(
                    text = stringResource(R.string.account_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = DwellSpacing.screenGutter)
                        .padding(bottom = DwellSpacing.xxl),
                ) {
                    SectionLabel(
                        text = stringResource(R.string.app_name),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(DwellSpacing.md))
                    DwellDisplayTitle(
                        text = stringResource(R.string.account_hero_headline),
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Spacer(Modifier.height(DwellSpacing.md))
                    Text(
                        text = stringResource(R.string.account_hero_subline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(DwellSpacing.section))

                    if (!showEmailForm) {
                        DwellPrimaryButton(
                            text = stringResource(R.string.account_continue_google),
                            onClick = {
                                scope.launch {
                                    val token = getGoogleIdToken(context)
                                    if (token != null) viewModel.submitGoogle(token)
                                }
                            },
                            enabled = !state.inProgress,
                            loading = state.inProgress,
                        )
                        Spacer(Modifier.height(DwellSpacing.md))
                        DwellSecondaryButton(
                            text = stringResource(R.string.account_continue_email),
                            onClick = { showEmailForm = true },
                        )
                        Spacer(Modifier.height(DwellSpacing.xl))
                        Text(
                            text = stringResource(R.string.account_legal),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        EmailForm(
                            state = state,
                            onSelectMode = viewModel::setMode,
                            onEmail = viewModel::onEmailChange,
                            onPassword = viewModel::onPasswordChange,
                            onSubmit = viewModel::submitEmail,
                        )
                        Spacer(Modifier.height(DwellSpacing.md))
                        DwellSecondaryButton(
                            text = stringResource(R.string.account_continue_google),
                            onClick = {
                                scope.launch {
                                    val token = getGoogleIdToken(context)
                                    if (token != null) viewModel.submitGoogle(token)
                                }
                            },
                            enabled = !state.inProgress,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailForm(
    state: SignInUiState,
    onSelectMode: (SignInMode) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    DwellCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(DwellSpacing.lg)) {
            DwellSegmentedToggle(
                options = listOf(
                    SignInMode.SignIn to stringResource(R.string.account_sign_in),
                    SignInMode.Create to stringResource(R.string.account_create),
                ),
                selected = state.mode,
                onSelect = onSelectMode,
            )
            Spacer(Modifier.height(DwellSpacing.lg))
            DwellTextField(
                value = state.email,
                onValueChange = onEmail,
                label = stringResource(R.string.account_email_hint),
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(DwellSpacing.sm))
            DwellTextField(
                value = state.password,
                onValueChange = onPassword,
                label = stringResource(R.string.account_password_hint),
                isPassword = true,
                imeAction = ImeAction.Done,
                error = state.inlineError?.let { stringResource(errorRes(it)) },
            )
            Spacer(Modifier.height(DwellSpacing.lg))
            DwellPrimaryButton(
                text = if (state.mode == SignInMode.Create) {
                    stringResource(R.string.account_create)
                } else {
                    stringResource(R.string.account_sign_in)
                },
                onClick = onSubmit,
                enabled = !state.inProgress && state.email.isNotBlank() && state.password.isNotBlank(),
                loading = state.inProgress,
            )
        }
    }
}

internal fun errorRes(e: AuthError): Int = when (e) {
    AuthError.INVALID_CREDENTIALS -> R.string.account_err_invalid
    AuthError.EMAIL_IN_USE -> R.string.account_err_email_in_use
    AuthError.WEAK_PASSWORD -> R.string.account_err_weak_password
    AuthError.INVALID_EMAIL -> R.string.account_err_invalid_email
    AuthError.NETWORK -> R.string.account_err_network
    AuthError.UNKNOWN -> R.string.account_err_unknown
}
