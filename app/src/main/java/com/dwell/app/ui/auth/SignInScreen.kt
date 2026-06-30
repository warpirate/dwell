package com.dwell.app.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dwell.app.R
import com.dwell.app.data.auth.AuthError
import com.dwell.app.ui.theme.DisplayFontFamily
import com.dwell.app.ui.theme.warmRadialBrush
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(state.done) {
        if (state.done) {
            if (state.mergedExisting) {
                Toast.makeText(context, R.string.account_merged_toast, Toast.LENGTH_LONG).show()
            }
            onSignedIn()
        }
    }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val brush = remember(dark, wPx, hPx) { warmRadialBrush(dark, wPx, hPx) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(top = 4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(Modifier.height(36.dp))

            // Eyebrow wordmark, caps + tracked, like the mockups' section labels.
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (state.mode == SignInMode.Create) {
                    stringResource(R.string.account_title_create)
                } else {
                    stringResource(R.string.account_title_signin)
                },
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.account_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            // The form sits on a warm layered card with a soft shadow — the
            // depth that flat fills were missing.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color(0xFF1C1810),
                        spotColor = Color(0xFF1C1810),
                        clip = false,
                    ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    ModeToggle(mode = state.mode, onSelect = viewModel::setMode)
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text(stringResource(R.string.account_email_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text(stringResource(R.string.account_password_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.inlineError != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(errorRes(state.inlineError!!)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = viewModel::submitEmail,
                        enabled = !state.inProgress && state.email.isNotBlank() && state.password.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        if (state.inProgress) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Text(
                                text = if (state.mode == SignInMode.Create) {
                                    stringResource(R.string.account_create)
                                } else {
                                    stringResource(R.string.account_sign_in)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            OrDivider()
            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val token = getGoogleIdToken(context)
                        if (token != null) viewModel.submitGoogle(token)
                    }
                },
                enabled = !state.inProgress,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(
                    stringResource(R.string.account_continue_google),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.account_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        Text(
            text = stringResource(R.string.account_or),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ModeToggle(mode: SignInMode, onSelect: (SignInMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeChip(stringResource(R.string.account_sign_in), mode == SignInMode.SignIn, Modifier.weight(1f)) {
            onSelect(SignInMode.SignIn)
        }
        ModeChip(stringResource(R.string.account_create), mode == SignInMode.Create, Modifier.weight(1f)) {
            onSelect(SignInMode.Create)
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = modifier.fillMaxSize(),
        ) { Text(label, style = MaterialTheme.typography.labelLarge) }
    } else {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(10.dp), modifier = modifier.fillMaxSize()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun errorRes(e: AuthError): Int = when (e) {
    AuthError.INVALID_CREDENTIALS -> R.string.account_err_invalid
    AuthError.EMAIL_IN_USE -> R.string.account_err_email_in_use
    AuthError.WEAK_PASSWORD -> R.string.account_err_weak_password
    AuthError.INVALID_EMAIL -> R.string.account_err_invalid_email
    AuthError.NETWORK -> R.string.account_err_network
    AuthError.UNKNOWN -> R.string.account_err_unknown
}
