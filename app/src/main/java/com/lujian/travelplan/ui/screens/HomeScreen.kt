package com.lujian.travelplan.ui.screens

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.map.MapViewportMode
import com.lujian.travelplan.map.MapViewportPolicy
import com.lujian.travelplan.map.MarkerClusterer
import com.lujian.travelplan.map.LUJIAN_MAP_STYLE_URL
import com.lujian.travelplan.map.LujianMapStyle
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.MarkerOptions

@Composable
fun HomeScreen(
    plans: List<StoredPlan>,
    onOpenPlan: (Long) -> Unit,
) {
    var retryKey by remember { mutableIntStateOf(0) }
    var selectedPlans by remember { mutableStateOf<List<StoredPlan>>(emptyList()) }
    val mappedPlans = remember(plans) {
        plans.flatMap { plan ->
            plan.parsed.destinations
                .filter { it.latitude != null && it.longitude != null }
                .map { destination -> plan to destination }
        }
    }
    val viewport = MapViewportPolicy.resolve(mappedPlans.map { it.second })

    Box(Modifier.fillMaxSize().background(Paper)) {
        MapLibreSurface(
            key = retryKey,
            plans = plans,
            viewport = viewport,
            onSelected = { markerPlans ->
                if (
                    markerPlans.size == 1 &&
                    selectedPlans.map { it.id } == markerPlans.map { it.id }
                ) {
                    onOpenPlan(markerPlans.single().id)
                } else {
                    selectedPlans = markerPlans
                }
            },
            onRetry = { retryKey++ },
        )

        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text("旅笺", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (viewport == MapViewportMode.CHINA) "CHINA · ${mappedPlans.size} 个足迹" else "WORLD · ${mappedPlans.size} 个足迹",
                style = MaterialTheme.typography.labelLarge,
                color = Coral,
            )
        }

        if (selectedPlans.isNotEmpty()) {
            PaperCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(18.dp),
                background = Paper,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Coral)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (selectedPlans.size == 1) selectedPlans.single().parsed.title else "这里有 ${selectedPlans.size} 份计划",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        selectedPlans.forEach { plan ->
                            androidx.compose.material3.TextButton(onClick = { onOpenPlan(plan.id) }) {
                                Text(plan.parsed.title)
                            }
                        }
                        if (selectedPlans.size == 1) {
                            Text("再次点击大头针进入计划", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapLibreSurface(
    key: Int,
    plans: List<StoredPlan>,
    viewport: MapViewportMode,
    onSelected: (List<StoredPlan>) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember(key) { MapView(context).apply { onCreate(Bundle()) } }
    var map by remember(key) { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember(key) { mutableStateOf(false) }
    var timedOut by remember(key) { mutableStateOf(false) }

    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(key) {
        delay(8_000)
        if (!ready) timedOut = true
    }

    LaunchedEffect(map, plans, viewport) {
        val currentMap = map ?: return@LaunchedEffect
        val pinIcon = LujianMapStyle.createPin(context)
        currentMap.markers.toList().forEach { currentMap.removeMarker(it) }
        val entries = plans.flatMap { plan ->
            plan.parsed.destinations
                .filter { it.latitude != null && it.longitude != null }
                .map { destination -> plan to destination }
        }
        val markerPlans = mutableMapOf<Long, List<StoredPlan>>()
        MarkerClusterer.cluster(entries.map { it.second }, radiusKm = 8.0).forEach { cluster ->
            val clusterPlans = entries
                .filter { (_, destination) -> destination in cluster.destinations }
                .map { it.first }
                .distinctBy { it.id }
            val marker = currentMap.addMarker(
                MarkerOptions()
                    .position(LatLng(cluster.latitude, cluster.longitude))
                    .icon(pinIcon)
                    .title(if (clusterPlans.size == 1) clusterPlans.single().parsed.title else "${clusterPlans.size} 份旅行计划")
                    .snippet(cluster.destinations.joinToString(" · ") { it.name }),
            )
            markerPlans[marker.id] = clusterPlans
        }
        currentMap.setOnMarkerClickListener { marker ->
            markerPlans[marker.id]?.let(onSelected)
            true
        }
        currentMap.cameraPosition = CameraPosition.Builder()
            .target(if (viewport == MapViewportMode.CHINA) LatLng(31.5, 104.5) else LatLng(15.0, 10.0))
            .zoom(if (viewport == MapViewportMode.CHINA) 3.25 else 1.0)
            .build()
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { mapLibreMap ->
                    mapLibreMap.setStyle(Style.Builder().fromUri(LUJIAN_MAP_STYLE_URL)) { style ->
                        LujianMapStyle.apply(style)
                        map = mapLibreMap
                        ready = true
                        timedOut = false
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    if (!ready) {
        Box(Modifier.fillMaxSize().background(Paper.copy(alpha = .9f)), contentAlignment = Alignment.Center) {
            PaperCard(modifier = Modifier.padding(32.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!timedOut) CircularProgressIndicator(color = Coral)
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Coral)
                    Text(if (timedOut) "地图暂时没有连上" else "正在展开地图", style = MaterialTheme.typography.titleLarge)
                    Text("本地计划仍可从计划库正常阅读", style = MaterialTheme.typography.bodyMedium)
                    if (timedOut) IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重试地图", tint = Ink)
                    }
                }
            }
        }
    }
}
