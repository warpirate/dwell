package com.dwell.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dwell.app.R

/**
 * The three bottom-nav destinations (UI/UX doc 3). Order is display order.
 * Wallpapers is the default landing screen.
 */
enum class DwellDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    WALLPAPERS("wallpapers", R.string.nav_wallpapers, R.drawable.ic_wallpapers),
    WIDGETS("widgets", R.string.nav_widgets, R.drawable.ic_widgets),
    MORE("more", R.string.nav_more, R.drawable.ic_more),
    ;

    companion object {
        val START = WALLPAPERS
    }
}
