package com.dwell.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.auth.UpgradeResult
import com.dwell.app.data.favorites.FavoriteRemote
import com.dwell.app.data.favorites.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun setMode(mode: SignInMode) = _uiState.update { it.copy(mode = mode, inlineError = null) }
    fun onEmailChange(v: String) = _uiState.update { it.copy(email = v, inlineError = null) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v, inlineError = null) }

    fun submitEmail() {
        val s = _uiState.value
        if (s.inProgress) return
        _uiState.update { it.copy(inProgress = true, inlineError = null) }
        viewModelScope.launch {
            // Capture anon favorites BEFORE any account switch (data-loss protocol).
            val snapshot = favorites.snapshotLocalFavorites()
            val result = auth.linkEmail(s.email.trim(), s.password, s.mode == SignInMode.Create)
            applyResult(result, snapshot)
        }
    }

    /** Called by the UI after a Google id token is obtained at the Activity boundary. */
    fun submitGoogle(idToken: String) {
        if (_uiState.value.inProgress) return
        _uiState.update { it.copy(inProgress = true, inlineError = null) }
        viewModelScope.launch {
            val snapshot = favorites.snapshotLocalFavorites()
            val result = auth.linkGoogle(idToken)
            applyResult(result, snapshot)
        }
    }

    private suspend fun applyResult(result: UpgradeResult, snapshot: List<FavoriteRemote>) {
        when (result) {
            is UpgradeResult.Linked -> {
                favorites.reconcile()
                _uiState.update { it.copy(inProgress = false, done = true) }
            }
            is UpgradeResult.SignedInExisting -> {
                favorites.mergeInto(result.uid, snapshot)
                favorites.reconcile()
                _uiState.update { it.copy(inProgress = false, done = true, mergedExisting = true) }
            }
            is UpgradeResult.Error -> {
                _uiState.update { it.copy(inProgress = false, inlineError = result.error) }
            }
        }
    }
}
