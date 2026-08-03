package com.lujian.travelplan.ui.screens

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lujian.travelplan.map.LUJIAN_MAP_STYLE_URL
import com.lujian.travelplan.map.LujianMapStyle
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Gold
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper
import kotlinx.coroutines.delay
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
internal fun DailyMapPage(
    plan: ParsedPlan,
    day: PlanDayDraft,
    focusedItemId: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val route = remember(plan, day) { buildDailyMapRoute(plan, day) }
    val mappedStops = route.filter { it.latitude != null && it.longitude != null }
    var retryKey by remember { mutableIntStateOf(0) }
    var map by remember(retryKey) { mutableStateOf<MapLibreMap?>(null) }

    Column(modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(day.label, color = Coral, style = MaterialTheme.typography.labelLarge)
            Text(day.title, style = MaterialTheme.typography.headlineMedium)
            Text(
                "${route.size} 个计划地点 · ${mappedStops.size} 个有效坐标",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = .62f),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(330.dp)
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Ink)
                .padding(3.dp)
                .clip(RoundedCornerShape(19.dp)),
        ) {
            if (mappedStops.isEmpty()) {
                MapEmptyState()
            } else {
                DailyMapSurface(
                    key = retryKey,
                    stops = route,
                    focusedItemId = focusedItemId,
                    onMapReady = { map = it },
                    onRetry = { retryKey++ },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(route, key = { _, stop -> stop.itemId }) { index, stop ->
                PaperCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (stop.latitude != null && stop.longitude != null) {
                                map?.easeCamera(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(stop.latitude, stop.longitude), 14.5),
                                    320,
                                )
                            }
                        },
                    background = if (stop.itemId == focusedItemId) Gold.copy(alpha = .34f) else Paper,
                    contentPadding = PaddingValues(13.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(String.format("%02d", index + 1), color = Coral, style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f)) {
                            Text(stop.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(stop.time, stop.category?.let(PlanReaderPresentation::categoryLabel)).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            stop.transport?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(4.dp))
                                Text("下一程 · $it", style = MaterialTheme.typography.bodySmall)
                            }
                            val externalUrl = stop.mapLinks.amap ?: stop.mapLinks.baidu
                            if (externalUrl != null) {
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)))
                                        }
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                ) { Text(if (stop.mapLinks.amap != null) "高德地图" else "百度地图") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyMapSurface(
    key: Int,
    stops: List<DailyMapStop>,
    focusedItemId: String?,
    onMapReady: (MapLibreMap) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val savedMapState = rememberSaveable(key) { Bundle() }
    val mapView = remember(key) { MapView(context).apply { onCreate(savedMapState) } }
    var map by remember(key) { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember(key) { mutableStateOf(false) }
    var timedOut by remember(key) { mutableStateOf(false) }

    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false
        var destroyed = false
        fun start() {
            if (!started && !destroyed) {
                mapView.onStart()
                started = true
            }
        }
        fun resume() {
            if (!resumed && !destroyed) {
                mapView.onResume()
                resumed = true
            }
        }
        fun pause() {
            if (resumed && !destroyed) {
                mapView.onPause()
                resumed = false
            }
        }
        fun stop() {
            if (started && !destroyed) {
                mapView.onStop()
                started = false
            }
        }
        fun destroy() {
            if (!destroyed) {
                mapView.onSaveInstanceState(savedMapState)
                pause()
                stop()
                mapView.onDestroy()
                destroyed = true
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                Lifecycle.Event.ON_DESTROY -> destroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose {
            lifecycle.removeObserver(observer)
            destroy()
        }
    }

    LaunchedEffect(key) {
        delay(8_000)
        if (!ready) timedOut = true
    }

    LaunchedEffect(map, stops, focusedItemId) {
        val currentMap = map ?: return@LaunchedEffect
        currentMap.markers.toList().forEach(currentMap::removeMarker)
        currentMap.polylines.toList().forEach(currentMap::removePolyline)
        val points = stops.mapNotNull { stop ->
            if (stop.latitude != null && stop.longitude != null) stop to LatLng(stop.latitude, stop.longitude) else null
        }
        val pinIcon = LujianMapStyle.createPin(context)
        points.forEachIndexed { index, (stop, point) ->
            currentMap.addMarker(
                MarkerOptions()
                    .position(point)
                    .icon(pinIcon)
                    .title("${index + 1} · ${stop.title}")
                    .snippet(stop.time.orEmpty()),
            )
        }
        if (points.size > 1) {
            currentMap.addPolyline(
                PolylineOptions()
                    .addAll(points.map { it.second })
                    .color(AndroidColor.parseColor("#FF6B4A"))
                    .width(4f),
            )
        }
        val focused = focusedItemId?.let { id -> points.firstOrNull { it.first.itemId == id }?.second }
        mapView.post {
            when {
                focused != null -> currentMap.easeCamera(CameraUpdateFactory.newLatLngZoom(focused, 14.5), 320)
                points.size == 1 -> currentMap.easeCamera(CameraUpdateFactory.newLatLngZoom(points.single().second, 14.0), 320)
                points.size > 1 -> {
                    val latitudes = points.map { it.second.latitude }
                    val longitudes = points.map { it.second.longitude }
                    val bounds = LatLngBounds.from(
                        latitudes.max(),
                        longitudes.max(),
                        latitudes.min(),
                        longitudes.min(),
                    )
                    currentMap.easeCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds, (34 * context.resources.displayMetrics.density).toInt()),
                        360,
                    )
                }
            }
        }
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
                        onMapReady(mapLibreMap)
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    if (!ready) {
        Box(Modifier.fillMaxSize().background(Paper.copy(alpha = .92f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!timedOut) CircularProgressIndicator(color = Coral)
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Coral)
                Text(if (timedOut) "地图暂时没有连上" else "正在展开当天地图")
                if (timedOut) IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = "重试地图")
                }
            }
        }
    }
}

@Composable
private fun MapEmptyState() {
    Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Coral)
            Text("当天地点还没有坐标", style = MaterialTheme.typography.titleLarge)
            Text("下方仍保留计划顺序和外部地图入口。", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
