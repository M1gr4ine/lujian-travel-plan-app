package com.lujian.travelplan.ui.screens

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.map.MapCameraPolicy
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

private fun markerInfoWindow(
    context: Context,
    plans: List<StoredPlan>,
    onOpenPlan: (Long) -> Unit,
): LinearLayout {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(Color.parseColor("#FAF6EF"))
            setStroke(dp(3), Color.parseColor("#2A2520"))
        }
        elevation = dp(8).toFloat()
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        val title = TextView(context).apply {
            text = if (plans.size == 1) plans.single().parsed.title else "这里有 ${plans.size} 份计划"
            setTextColor(Color.parseColor("#2A2520"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxWidth = dp(230)
        }
        addView(title)

        if (plans.size == 1) {
            title.setOnClickListener { onOpenPlan(plans.single().id) }
            addView(TextView(context).apply {
                text = "再点大头针进入"
                setTextColor(Color.parseColor("#6B6354"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        } else {
            plans.forEach { plan ->
                addView(TextView(context).apply {
                    text = plan.parsed.title
                    setTextColor(Color.parseColor("#FF6B4A"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    maxWidth = dp(230)
                    setPadding(dp(4), dp(5), dp(4), dp(2))
                    setOnClickListener { onOpenPlan(plan.id) }
                })
            }
        }

        alpha = 0f
        scaleX = .92f
        scaleY = .92f
        post {
            pivotX = width / 2f
            pivotY = height.toFloat()
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(170)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}
