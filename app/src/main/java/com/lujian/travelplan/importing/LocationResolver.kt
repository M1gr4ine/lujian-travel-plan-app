package com.lujian.travelplan.importing

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class LocationCandidate(
    val destinationName: String,
    val displayName: String,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double,
)

class LocationResolver(context: Context) {
    private val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)

    suspend fun resolve(destinationName: String): List<LocationCandidate> = runCatching {
        val addresses = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(destinationName, 5) { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(destinationName, 5).orEmpty()
            }
        }
        addresses.map { address -> address.toCandidate(destinationName) }
            .distinctBy { "${it.latitude},${it.longitude}" }
    }.getOrDefault(emptyList())

    private fun Address.toCandidate(destinationName: String): LocationCandidate = LocationCandidate(
        destinationName = destinationName,
        displayName = listOfNotNull(locality, subAdminArea, adminArea, countryName)
            .distinct()
            .joinToString(" · ")
            .ifBlank { destinationName },
        countryCode = countryCode,
        latitude = latitude,
        longitude = longitude,
    )
}
