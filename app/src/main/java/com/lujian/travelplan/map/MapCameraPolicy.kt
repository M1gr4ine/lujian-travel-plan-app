package com.lujian.travelplan.map

data class MapCameraBounds(
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double,
) {
    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in south..north && longitude in west..east
}

object MapCameraPolicy {
    private val china = MapCameraBounds(
        north = 54.0,
        east = 135.0,
        south = 18.0,
        west = 73.0,
    )
    private val world = MapCameraBounds(
        north = 80.0,
        east = 179.0,
        south = -60.0,
        west = -179.0,
    )

    fun boundsFor(mode: MapViewportMode): MapCameraBounds =
        if (mode == MapViewportMode.CHINA) china else world
}
