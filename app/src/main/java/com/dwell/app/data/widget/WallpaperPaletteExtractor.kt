package com.dwell.app.data.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The wallpaper-match moat, on-device. Because Dwell *owns* the wallpaper bitmap the moment a
 * user applies one, we can read its palette directly — something a generic widget app can't do
 * (Android 14+ blocks `WallpaperManager.getDrawable()` for normal apps). We sample a swatch with
 * AndroidX [Palette], then hand it to [WallpaperMatch] to become a legible widget text colour.
 */
object WallpaperPaletteExtractor {

    /** Extract a legibility-coerced widget text colour from a wallpaper [bitmap]. */
    suspend fun match(bitmap: Bitmap): Int = withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap).generate()
        val seed = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: 0xFFECE7DD.toInt()
        WallpaperMatch.coerceTextColor(seed)
    }
}

/** Which stand-in wallpaper to match (POC — real path samples the applied wallpaper). */
enum class WallpaperSample { DUSK, FOREST }

/**
 * Generates the stand-in wallpaper bitmaps for the POC so we can prove the full
 * bitmap -> Palette -> coerce -> render pipeline on device without the wallpaper apply flow.
 * The gradients mirror the "Dusk" and "Forest" swatches from the approved paywall mock.
 */
object SampleWallpaper {

    fun of(sample: WallpaperSample): Bitmap = when (sample) {
        WallpaperSample.DUSK -> gradient(0xFFC98A6A.toInt(), 0xFF8A5A6E.toInt(), 0xFF3D3550.toInt())
        WallpaperSample.FOREST -> gradient(0xFF6D8A6F.toInt(), 0xFF3F5F4A.toInt(), 0xFF20302A.toInt())
    }

    private fun gradient(vararg colors: Int): Bitmap {
        val w = 96; val h = 192
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        }
        Canvas(bmp).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }
}
