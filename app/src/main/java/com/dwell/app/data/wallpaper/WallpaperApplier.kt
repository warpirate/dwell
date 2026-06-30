package com.dwell.app.data.wallpaper

/** Where an applied wallpaper lands. Maps to WallpaperManager flags. */
enum class WallpaperTarget { HOME, LOCK, BOTH }

/**
 * Applies a wallpaper image to the device. The one place that talks to
 * WallpaperManager, so the apply path (download + decode + set) stays out of
 * the UI layer (Architecture rule: side effects live behind a seam, not in
 * composables or view models directly).
 */
interface WallpaperApplier {

    /**
     * Loads [url] to a bitmap and sets it on [target]. Returns failure if the
     * image can't be loaded or the system rejects the set.
     */
    suspend fun apply(url: String, target: WallpaperTarget): Result<Unit>
}
