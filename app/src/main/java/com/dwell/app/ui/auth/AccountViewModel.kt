package com.dwell.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.favorites.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountState(val isSignedIn: Boolean, val email: String?)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountState(!auth.isAnonymous(), auth.currentEmail()))
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        auth.uid.onEach {
            _state.value = AccountState(!auth.isAnonymous(), auth.currentEmail())
        }.launchIn(viewModelScope)
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            favorites.clearLocal()
            favorites.reconcile()
        }
    }
}
