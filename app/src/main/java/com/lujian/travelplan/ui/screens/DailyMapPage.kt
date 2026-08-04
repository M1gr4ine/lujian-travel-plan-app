package com.lujian.travelplan.ui.screens

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lujian.travelplan.map.LujianMapStyle
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.ui.components.LujianMapControls
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.components.LujianPinMark
import com.lujian.travelplan.ui.components.createLujianMapInfoWindow
import com.lujian.travelplan.ui.components.rememberLujianMapView
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Gold
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper
import kotlinx.coroutines.delay
import org.maplibre.android.annotations.Marker
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
    onDragEnabledChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val route = remember(plan, day) { buildDailyMapPresentation(plan, day) }
    val mappedStops = route.stops.filter { it.latitude != null && it.longitude != null }
    val destinationCenter = remember(plan.destinations) {
        plan.destinations.firstOrNull { it.latitude != null && it.longitude != null }
            ?.let { LatLng(requireNotNull(it.latitude), requireNotNull(it.longitude)) }
    }
    var retryKey by remember { mutableIntStateOf(0) }
    var map by remember(retryKey) { mutableStateOf<MapLibreMap?>(null) }
    var dragEnabled by remember(day.id, retryKey) { mutableStateOf(false) }
    var selectedItemId by remember(day.id) {
        mutableStateOf(focusedItemId?.takeIf { id -> route.stops.any { it.itemId == id } })
    }
    var focusRequest by remember(day.id) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val markerHitRadius = 40 * LocalContext.current.resources.displayMetrics.density
    val currentOnDragEnabledChange by rememberUpdatedState(onDragEnabledChange)

    LaunchedEffect(day.id) {
        listState.scrollToItem(0)
        dragEnabled = false
    }

    LaunchedEffect(day.id, dragEnabled) {
        currentOnDragEnabledChange(dragEnabled)
    }

    LaunchedEffect(day.id, focusedItemId) {
        val nextItemId = focusedItemId?.takeIf { id -> route.stops.any { it.itemId == id } }
        if (nextItemId != selectedItemId) {
            selectedItemId = nextItemId
            if (nextItemId != null) focusRequest++
        }
    }

    val requestStopFocus: (String) -> Unit = { itemId ->
        selectedItemId = itemId
        focusRequest++
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(Paper),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = shouldEnableDailyMapListScroll(dragEnabled),
    ) {
        item(key = "header-${day.id}") {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(day.label, color = Coral, style = MaterialTheme.typography.labelLarge)
                Text(day.title, style = MaterialTheme.typography.headlineMedium)
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapStatCard(route.stops.size.toString(), "地点", Modifier.weight(1f))
                    MapStatCard(route.distanceEstimate, "预计里程", Modifier.weight(1f))
                    MapStatCard(route.durationEstimate, "移动时间", Modifier.weight(1f))
                }
            }
        }
        item(key = "map-${day.id}") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (mappedStops.isEmpty() && destinationCenter == null) 220.dp else 300.dp)
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Ink)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(19.dp)),
            ) {
                if (mappedStops.isEmpty() && destinationCenter == null) {
                    MapEmptyState()
                } else {
                    DailyMapSurface(
                        key = retryKey,
                        stops = route.stops,
                        legs = route.legs,
                        fallbackCenter = destinationCenter,
                        focusedItemId = selectedItemId,
                        focusRequest = focusRequest,
                        dragEnabled = dragEnabled,
                        onStopSelected = requestStopFocus,
                        onMapReady = { map = it },
                        onRetry = { retryKey++ },
                    )
                    if (mappedStops.isEmpty()) {
                        PaperCard(
                            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).fillMaxWidth(.72f),
                            background = Paper.copy(alpha = .94f),
                            contentPadding = PaddingValues(10.dp),
                        ) {
                            Text("当天地点坐标待补，先显示目的地底图。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!dragEnabled) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .semantics { contentDescription = "路线地图预览" }
                                .pointerInput(listState) {
                                    detectVerticalDragGestures { change, dragAmount ->
                                        change.consume()
                                        listState.dispatchRawDelta(-dragAmount)
                                    }
                                }
                                .pointerInput(map, mappedStops, markerHitRadius) {
                                    detectTapGestures { position ->
                                        nearestMapStop(map, mappedStops, position, markerHitRadius)?.let { stop ->
                                            requestStopFocus(stop.itemId)
                                        }
                                    }
                                },
                        )
                    }
                    LujianMapControls(
                        dragEnabled = dragEnabled,
                        enabled = map != null,
                        onZoomIn = { map?.easeCamera(CameraUpdateFactory.zoomIn(), 180) },
                        onZoomOut = { map?.easeCamera(CameraUpdateFactory.zoomOut(), 180) },
                        onToggleDrag = { dragEnabled = !dragEnabled },
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    )
                }
            }
        }
        if (route.legs.isNotEmpty()) {
            itemsIndexed(route.legs, key = { _, leg -> leg.id }) { index, leg ->
                RouteLegCard(
                    leg = leg,
                    index = index,
                    focusedItemId = selectedItemId,
                    onFocus = { requestStopFocus(leg.toStop.itemId) },
                )
            }
        } else {
            itemsIndexed(route.stops, key = { _, stop -> stop.itemId }) { index, stop ->
                MapStopCard(
                    stop = stop,
                    index = index,
                    focusedItemId = selectedItemId,
                    onFocus = { requestStopFocus(stop.itemId) },
                )
            }
        }
    }
}

@Composable
private fun MapStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    PaperCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Column {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = .58f))
        }
    }
}

@Composable
private fun RouteLegCard(
    leg: DailyMapLeg,
    index: Int,
    focusedItemId: String?,
    onFocus: () -> Unit,
) {
    val isFocused = leg.toStop.itemId == focusedItemId
    PaperCard(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .semantics {
                selected = isFocused
                contentDescription = "路线地点：" + leg.toStop.title
            }
            .clickable(onClick = onFocus),
        background = if (isFocused) Gold.copy(alpha = .34f) else Paper,
        contentPadding = PaddingValues(13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RouteNumberPin(index + 1)
            Column(Modifier.weight(1f)) {
                Text(
                    "${leg.fromStop.title} → ${leg.toStop.title}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(leg.summary, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = .66f))
            }
        }
    }
}

@Composable
private fun RouteNumberPin(number: Int) {
    Box(
        Modifier
            .size(28.dp)
            .background(Coral, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), color = Paper, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MapStopCard(
    stop: DailyMapStop,
    index: Int,
    focusedItemId: String?,
    onFocus: () -> Unit,
) {
    val context = LocalContext.current
    val isFocused = stop.itemId == focusedItemId
    PaperCard(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .semantics {
                selected = isFocused
                contentDescription = "路线地点：" + stop.title
            }
            .clickable(onClick = onFocus),
        background = if (isFocused) Gold.copy(alpha = .34f) else Paper,
        contentPadding = PaddingValues(13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RouteNumberPin(index + 1)
            Column(Modifier.weight(1f)) {
                Text(stop.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(stop.time, stop.category?.let(PlanReaderPresentation::categoryLabel)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                )
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

private fun nearestMapStop(
    map: MapLibreMap?,
    stops: List<DailyMapStop>,
    position: Offset,
    hitRadius: Float,
): DailyMapStop? {
    val currentMap = map ?: return null
    return stops.mapNotNull { stop ->
        val point = stop.toLatLngOrNull() ?: return@mapNotNull null
        val screenPoint = currentMap.projection.toScreenLocation(point)
        val distanceSquared =
            (screenPoint.x - position.x) * (screenPoint.x - position.x) +
                (screenPoint.y - position.y) * (screenPoint.y - position.y)
        stop to distanceSquared
    }.filter { (_, distanceSquared) -> distanceSquared <= hitRadius * hitRadius }
        .minByOrNull { (_, distanceSquared) -> distanceSquared }
        ?.first
}

private fun focusMapMarker(
    map: MapLibreMap,
    mapView: MapView,
    marker: Marker,
    stop: DailyMapStop,
) {
    val point = stop.toLatLngOrNull() ?: return
    focusSingleMapInfoWindow(
        markers = map.markers,
        isShown = Marker::isInfoWindowShown,
        hide = Marker::hideInfoWindow,
        center = { onFinished ->
            map.cancelTransitions()
            map.easeCamera(
                CameraUpdateFactory.newLatLngZoom(point, 14.5),
                320,
                object : MapLibreMap.CancelableCallback {
                    override fun onCancel() = Unit

                    override fun onFinish() = onFinished()
                },
            )
        },
        show = { marker.showInfoWindow(map, mapView) },
    )
}

private fun DailyMapStop.toLatLngOrNull(): LatLng? =
    if (latitude != null && longitude != null) LatLng(latitude, longitude) else null

@Composable
private fun DailyMapSurface(
    key: Int,
    stops: List<DailyMapStop>,
    legs: List<DailyMapLeg>,
    fallbackCenter: LatLng?,
    focusedItemId: String?,
    focusRequest: Int,
    dragEnabled: Boolean,
    onStopSelected: (String) -> Unit,
    onMapReady: (MapLibreMap) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val mapView = rememberLujianMapView(key)
    var map by remember(key) { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember(key) { mutableStateOf(false) }
    var timedOut by remember(key) { mutableStateOf(false) }
    var markerRevision by remember(key) { mutableIntStateOf(0) }
    val markersByItemId = remember(key) { mutableMapOf<String, Marker>() }
    val currentOnStopSelected by rememberUpdatedState(onStopSelected)

    LaunchedEffect(key) {
        delay(8_000)
        if (!ready) timedOut = true
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
                        Marker::isInfoWindowShown,
                        Marker::hideInfoWindow,
                    )
                }
            }
            currentMap.addOnCameraMoveStartedListener(listener)
            onDispose { currentMap.removeOnCameraMoveStartedListener(listener) }
        }
    }

    LaunchedEffect(map, stops, legs, fallbackCenter) {
        val currentMap = map ?: return@LaunchedEffect
        currentMap.markers.filter(Marker::isInfoWindowShown).forEach(Marker::hideInfoWindow)
        currentMap.markers.toList().forEach(currentMap::removeMarker)
        currentMap.polylines.toList().forEach(currentMap::removePolyline)
        markersByItemId.clear()
        val points = stops.mapNotNull { stop ->
            if (stop.latitude != null && stop.longitude != null) stop to LatLng(stop.latitude, stop.longitude) else null
        }
        val pinIcon = LujianMapStyle.createPin(context)
        val markerDetails = mutableMapOf<Long, Pair<Int, DailyMapStop>>()
        points.forEachIndexed { index, (stop, point) ->
            val marker = currentMap.addMarker(
                MarkerOptions()
                    .position(point)
                    .icon(pinIcon)
                    .title((index + 1).toString() + " · " + stop.title)
                    .snippet(stop.time.orEmpty()),
            )
            markersByItemId[stop.itemId] = marker
            markerDetails[marker.id] = index + 1 to stop
        }
        currentMap.setInfoWindowAdapter { marker ->
            val detail = markerDetails[marker.id]
            if (detail == null) {
                createLujianMapInfoWindow(context, marker.title.orEmpty(), marker.snippet)
            } else {
                val (number, stop) = detail
                createLujianMapInfoWindow(
                    context = context,
                    title = number.toString() + " · " + stop.title,
                    subtitle = listOfNotNull(
                        stop.time?.takeIf(String::isNotBlank),
                        stop.category?.let(PlanReaderPresentation::categoryLabel),
                    ).joinToString(" · ").takeIf(String::isNotBlank),
                )
            }
        }
        currentMap.setOnMarkerClickListener { marker ->
            val stop = markerDetails[marker.id]?.second ?: return@setOnMarkerClickListener false
            currentOnStopSelected(stop.itemId)
            true
        }
        legs.forEach { leg ->
            val from = leg.fromStop.toLatLngOrNull() ?: return@forEach
            val to = leg.toStop.toLatLngOrNull() ?: return@forEach
            val color = when (leg.mode) {
                "walk" -> "#4A8F63"
                "drive" -> "#D45D3D"
                else -> "#2D73C8"
            }
            currentMap.addPolyline(
                PolylineOptions()
                    .addAll(listOf(from, to))
                    .color(AndroidColor.parseColor(color))
                    .width(4f),
            )
        }
        mapView.post {
            when {
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
                fallbackCenter != null -> currentMap.easeCamera(CameraUpdateFactory.newLatLngZoom(fallbackCenter, 11.5), 320)
            }
        }
        markerRevision++
    }


    LaunchedEffect(map, focusedItemId, focusRequest, markerRevision) {
        val currentMap = map ?: return@LaunchedEffect
        val selectedId = focusedItemId ?: return@LaunchedEffect
        val stop = stops.firstOrNull { it.itemId == selectedId } ?: return@LaunchedEffect
        val marker = markersByItemId[selectedId] ?: return@LaunchedEffect
        mapView.post { focusMapMarker(currentMap, mapView, marker, stop) }
    }
    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { mapLibreMap ->
                    mapLibreMap.uiSettings.apply {
                        isScrollGesturesEnabled = dragEnabled
                        isZoomGesturesEnabled = false
                        isRotateGesturesEnabled = false
                        isTiltGesturesEnabled = false
                    }
                    mapLibreMap.setStyle(LujianMapStyle.styleBuilder(context)) { style ->
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
                LujianPinMark(Modifier.height(32.dp).fillMaxWidth())
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
            LujianPinMark(Modifier.height(34.dp).fillMaxWidth())
            Text("当天地点还没有坐标", style = MaterialTheme.typography.titleLarge)
            Text("下方仍保留计划路线和文字说明。", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
