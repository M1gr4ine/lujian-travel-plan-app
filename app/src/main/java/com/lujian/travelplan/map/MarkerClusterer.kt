package com.lujian.travelplan.map

import com.lujian.travelplan.model.DestinationDraft
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DestinationCluster(
    val latitude: Double,
    val longitude: Double,
    val destinations: List<DestinationDraft>,
)

object MarkerClusterer {
    fun cluster(
        destinations: List<DestinationDraft>,
        radiusKm: Double,
    ): List<DestinationCluster> {
        val clusters = mutableListOf<MutableList<DestinationDraft>>()
        destinations.filter { it.latitude != null && it.longitude != null }.forEach { destination ->
            val matching = clusters.firstOrNull { cluster ->
                val centerLat = cluster.mapNotNull { it.latitude }.average()
                val centerLng = cluster.mapNotNull { it.longitude }.average()
                distanceKm(centerLat, centerLng, destination.latitude!!, destination.longitude!!) <= radiusKm
            }
            if (matching == null) clusters += mutableListOf(destination) else matching += destination
        }
        return clusters.map { cluster ->
            DestinationCluster(
                latitude = cluster.mapNotNull { it.latitude }.average(),
                longitude = cluster.mapNotNull { it.longitude }.average(),
                destinations = cluster,
            )
        }
    }

    private fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
