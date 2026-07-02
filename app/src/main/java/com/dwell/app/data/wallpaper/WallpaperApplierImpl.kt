package com.dwell.app.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.dwell.app.data.widget.WallpaperMatchStore
import com.dwell.app.data.widget.WallpaperPaletteExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperApplierImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val matchStore: WallpaperMatchStore,
) : WallpaperApplier {

    override suspend fun apply(url: String, target: WallpaperTarget): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // allowHardware(false): WallpaperManager reads pixels off the
                // bitmap, which a hardware bitmap won't allow.
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = SingletonImageLoader.get(context).execute(request)
                val bitmap = (result as? SuccessResult)?.image?.toBitmap()
                    ?: error("Could not load wallpaper image")

                val manager = WallpaperManager.getInstance(context)
                manager.setBitmap(
                    bitmap,
                    /* visibleCropHint = */ null,
                    /* allowBackup = */ true,
                    target.toFlags(),
                )

                // The moat: we own this bitmap, so extract its matched widget colour now and
                // persist it. A palette failure must not fail the apply — the wallpaper is set.
                runCatching { matchStore.save(WallpaperPaletteExtractor.match(bitmap)) }
                Unit
            }
        }

    private fun WallpaperTarget.toFlags(): Int = when (this) {
        WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
        WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
        WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }
}
