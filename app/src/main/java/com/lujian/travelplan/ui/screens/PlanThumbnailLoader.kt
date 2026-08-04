package com.lujian.travelplan.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object PlanThumbnailLoader {
    private const val MaxCachedThumbnails = 8
    private val cache = object : LinkedHashMap<String, Bitmap>(MaxCachedThumbnails, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > MaxCachedThumbnails
    }

    suspend fun decode(
        file: File,
        decoder: (String) -> Bitmap? = { BitmapFactory.decodeFile(it) },
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        decoder(file.absolutePath)?.also { bitmap ->
            synchronized(cache) {
                cache.keys.removeAll { it.startsWith("${file.absolutePath}:") }
                cache[key] = bitmap
            }
        }
    }
}
