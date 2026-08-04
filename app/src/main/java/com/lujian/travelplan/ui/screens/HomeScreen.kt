package com.lujian.travelplan.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.map.MapCameraPolicy
import com.lujian.travelplan.map.MapViewportMode
import com.lujian.travelplan.map.MapViewportPolicy
import com.lujian.travelplan.map.MarkerClusterer
import com.lujian.travelplan.map.LujianMapStyle
import com.lujian.travelplan.ui.components.LujianMapControls
import com.lujian.travelplan.ui.components.LujianMapInfoAction
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.components.LujianPinMark
import com.lujian.travelplan.ui.components.createLujianMapInfoWindow
import com.lujian.travelplan.ui.components.rememberLujianMapView
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.MarkerOptions

@Composable
fun HomeScreen(
    plans: List<StoredPlan>,
    onOpenPlan: (Long) -> Unit,
    onDragEnabledChange: (Boolean) -> Unit = {},
) {
    var retryKey by remember { mutableIntStateOf(0) }
    val mappedPlans = remember(plans) {
        plans.flatMap { plan ->
            plan.parsed.destinations
                .filter { it.latitude != null && it.longitude != null }
                .map { destination -> plan to destination }
        }
    }
    val viewport = MapViewportPolicy.resolve(mappedPlans.map { it.second })

    Column(Modifier.fillMaxSize().background(Paper)) {
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

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Ink)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                MapLibreSurface(
                    key = retryKey,
                    plans = plans,
                    viewport = viewport,
                    onOpenPlan = onOpenPlan,
                    onDragEnabledChange = onDragEnabledChange,
                    onRetry = { retryKey++ },
                )
            }
        }
    }
}

@Composable
private fun MapLibreSurface(
    key: Int,
    plans: List<StoredPlan>,
    viewport: MapViewportMode,
    onOpenPlan: (Long) -> Unit,
    onDragEnabledChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val mapView = rememberLujianMapView(key)
    var map by remember(key) { mutableStateOf<MapLibreMap?>(null) }
    var dragEnabled by remember(key) { mutableStateOf(false) }
    val currentOnDragEnabledChange by rememberUpdatedState(onDragEnabledChange)
    var ready by remember(key) { mutableStateOf(false) }
    var timedOut by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        delay(8_000)
        if (!ready) timedOut = true
    }

    LaunchedEffect(key, dragEnabled) {
        currentOnDragEnabledChange(dragEnabled)
    }

    LaunchedEffect(map, dragEnabled) {
        val currentMap = map ?: return@LaunchedEffect
        applyDailyMapDragMode(
            dragEnabled = dragEnabled,
            cancelTransitions = currentMap::cancelTransitions,
            configureGestures = { scrollEnabled, flingEnabled ->
                currentMap.uiSettings.apply {
                    isScrollGesturesEnabled = scrollEnabled
                    isFlingVelocityAnimationEnabled = flingEnabled
                    isZoomGesturesEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                }
            },
        )
    }

    DisposableEffect(map) {
        val currentMap = map
        if (currentMap == null) {
            onDispose { }
        } else {
            val listener = MapLibreMap.OnCameraMoveStartedListener { reason ->
                if (shouldDismissMapInfoWindowOnCameraMove(
                        reason,
                        MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE,
                    )
                ) {
                    dismissVisibleMapInfoWindows(
                        currentMap.markers,
                        { marker -> marker.isInfoWindowShown },
                        { marker -> marker.hideInfoWindow() },
                    )
                }
            }
            currentMap.addOnCameraMoveStartedListener(listener)
            onDispose { currentMap.removeOnCameraMoveStartedListener(listener) }
        }
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
        currentMap.setInfoWindowAdapter { marker ->
            markerInfoWindow(context, markerPlans[marker.id].orEmpty(), onOpenPlan)
        }
        currentMap.setOnMarkerClickListener { marker ->
            val plansAtMarker = markerPlans[marker.id].orEmpty()
            if (marker.isInfoWindowShown && plansAtMarker.size == 1) {
                onOpenPlan(plansAtMarker.single().id)
                true
            } else {
                false
            }
        }
        currentMap.setOnInfoWindowClickListener { marker ->
            val plansAtMarker = markerPlans[marker.id].orEmpty()
            if (plansAtMarker.size == 1) {
                onOpenPlan(plansAtMarker.single().id)
                true
            } else {
                false
            }
        }
        val cameraBounds = MapCameraPolicy.boundsFor(viewport)
        val mapBounds = LatLngBounds.from(
            cameraBounds.north,
            cameraBounds.east,
            cameraBounds.south,
            cameraBounds.west,
        )
        val paddingPx = (28 * context.resources.displayMetrics.density).toInt()
        mapView.post {
            currentMap.easeCamera(
                CameraUpdateFactory.newLatLngBounds(mapBounds, paddingPx),
                420,
            )
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { mapLibreMap ->
                    mapLibreMap.setStyle(LujianMapStyle.styleBuilder(context)) { style ->
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

    Box(
        Modifier.fillMaxWidth().padding(12.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        LujianMapControls(
            dragEnabled = dragEnabled,
            enabled = ready && map != null,
            onZoomIn = { map?.easeCamera(CameraUpdateFactory.zoomIn(), 180) },
            onZoomOut = { map?.easeCamera(CameraUpdateFactory.zoomOut(), 180) },
            onToggleDrag = { dragEnabled = !dragEnabled },
        )
    }

    if (!ready) {
        Box(Modifier.fillMaxSize().background(Paper.copy(alpha = .9f)), contentAlignment = Alignment.Center) {
            PaperCard(modifier = Modifier.padding(32.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!timedOut) CircularProgressIndicator(color = Coral)
                    LujianPinMark(Modifier.height(36.dp).fillMaxWidth())
                    Text(if (timedOut) "地图暂时没有连上" else "正在展开地图", style = MaterialTheme.typography.titleLarge)
                    Text("本地计划仍可从旅笺板正常阅读", style = MaterialTheme.typography.bodyMedium)
                    if (timedOut) IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重试地图", tint = Ink)
                    }
                }
            }
        }
    }
}

private fun markerInfoWindow(
    context: Context,
    plans: List<StoredPlan>,
    onOpenPlan: (Long) -> Unit,
) = createLujianMapInfoWindow(
    context = context,
    title = if (plans.size == 1) plans.single().parsed.title else "这里有 ${plans.size} 份计划",
    subtitle = "再点大头针进入".takeIf { plans.size == 1 },
    onTitleClick = plans.singleOrNull()?.let { plan -> { onOpenPlan(plan.id) } },
    actions = if (plans.size > 1) {
        plans.map { plan -> LujianMapInfoAction(plan.parsed.title) { onOpenPlan(plan.id) } }
    } else {
        emptyList()
    },
)
