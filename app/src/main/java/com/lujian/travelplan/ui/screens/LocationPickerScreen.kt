@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lujian.travelplan.ui.screens

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lujian.travelplan.data.PlanRepository
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.importing.LocationCandidate
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Paper
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun LocationPickerScreen(
    plan: StoredPlan,
    repository: PlanRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }
    var selected by remember { mutableStateOf<LatLng?>(null) }
    val scope = rememberCoroutineScope()
    val destinationName = plan.parsed.destinations.firstOrNull { it.latitude == null || it.longitude == null }?.name
        ?: plan.parsed.destinations.firstOrNull()?.name
        ?: "目的地"

    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("点选 $destinationName") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Paper)) {
            AndroidView(
                factory = {
                    mapView.apply {
                        getMapAsync { map ->
                            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) {
                                map.cameraPosition = CameraPosition.Builder().target(LatLng(35.5, 104.0)).zoom(3.1).build()
                                map.addOnMapClickListener { point -> selected = point; true }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            PaperCard(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            ) {
                Column {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Coral)
                    Text(
                        selected?.let { "已选 ${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" }
                            ?: "在地图上点击目的地位置",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        enabled = selected != null,
                        onClick = {
                            val point = selected ?: return@Button
                            scope.launch {
                                repository.confirmLocation(
                                    plan.id,
                                    LocationCandidate(
                                        destinationName = destinationName,
                                        displayName = destinationName,
                                        countryCode = if (point.latitude in 18.0..54.0 && point.longitude in 73.0..135.0) "CN" else null,
                                        latitude = point.latitude,
                                        longitude = point.longitude,
                                    ),
                                )
                                onBack()
                            }
                        },
                    ) { Text("确认位置") }
                }
            }
        }
    }
}
