package com.dwell.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.favorites.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs once on launch: ensure an anonymous session exists, then reconcile the
 * favorites cache from the server. Both steps are safe offline (no-ops).
 */
@HiltViewModel
class AppBootstrapViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            auth.ensureSignedIn()
            favorites.reconcile()
        }
    }
}
