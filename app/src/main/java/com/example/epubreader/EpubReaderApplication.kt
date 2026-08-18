package com.example.epubreader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class EpubReaderApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30) // 30% available RAM for blazing fast image scrolling
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(350L * 1024 * 1024) // 350 MB disk cache
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            }
            .respectCacheHeaders(false) // Keep Bangumi & WebDav cover caches alive
            .crossfade(150)
            .build()
    }
}
