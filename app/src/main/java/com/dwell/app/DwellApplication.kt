package com.dwell.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath

/**
 * Application entry point. @HiltAndroidApp generates the DI container.
 * Firebase auto-initializes from google-services.json via its content provider.
 *
 * Also configures the Coil singleton image loader with an OkHttp network
 * fetcher and an on-disk cache so viewed thumbnails render offline (TRD 4).
 */
@HiltAndroidApp
class DwellApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(64L * 1024 * 1024) // 64 MB
                    .build()
            }
            .crossfade(true)
            .build()
}
