package com.lujian.travelplan.map

import com.lujian.travelplan.model.DestinationDraft

enum class MapViewportMode {
    CHINA,
    WORLD,
}

object MapViewportPolicy {
    fun resolve(destinations: List<DestinationDraft>): MapViewportMode =
        if (destinations.any { destination ->
                destination.countryCode?.let { !it.equals("CN", ignoreCase = true) }
                    ?: outsideChinaBounds(destination)
            }
        ) {
            MapViewportMode.WORLD
        } else {
            MapViewportMode.CHINA
        }

    private fun outsideChinaBounds(destination: DestinationDraft): Boolean {
        val latitude = destination.latitude ?: return false
        val longitude = destination.longitude ?: return false
        return latitude !in 18.0..54.0 || longitude !in 73.0..135.0
    }
}
