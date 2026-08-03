package com.lujian.travelplan.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lujian.travelplan.AppGraph
import com.lujian.travelplan.importing.DuplicateResolution
import com.lujian.travelplan.importing.ImportResult
import com.lujian.travelplan.importing.LocationCandidate
import com.lujian.travelplan.ui.screens.EditPlanScreen
import com.lujian.travelplan.ui.screens.HomeScreen
import com.lujian.travelplan.ui.screens.LocationPickerScreen
import com.lujian.travelplan.ui.screens.PlanDetailScreen
import com.lujian.travelplan.ui.screens.PlanLibraryScreen
import com.lujian.travelplan.ui.screens.ProfileScreen
import com.lujian.travelplan.ui.screens.WebPlanScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class RootDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME("home", "首页", Icons.Outlined.Home, Icons.Rounded.Home),
    LIBRARY("library", "计划库", Icons.Outlined.Luggage, Icons.Rounded.Luggage),
    PROFILE("profile", "我", Icons.Outlined.AccountCircle, Icons.Rounded.AccountCircle),
}

@Composable
fun LujianRoot(
    graph: AppGraph,
    incomingUri: StateFlow<Uri?>,
    reduceMotion: Boolean,
    onIncomingUriHandled: () -> Unit,
) {
    var splashDone by remember { mutableStateOf(false) }
    if (!splashDone) {
        BrandSplash(reduceMotion, onFinished = { splashDone = true })
        return
    }
    LujianApp(graph, incomingUri, onIncomingUriHandled)
}

@Composable
private fun LujianApp(
    graph: AppGraph,
    incomingUri: StateFlow<Uri?>,
    onIncomingUriHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val plans by graph.repository.observePlans().collectAsState(initial = emptyList())
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val rootRoutes = RootDestination.entries.map { it.route }
    val scope = rememberCoroutineScope()
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var duplicateUri by remember { mutableStateOf<Uri?>(null) }
    var duplicate by remember { mutableStateOf<ImportResult.Duplicate?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var locationConfirmation by remember { mutableStateOf<Pair<Long, List<LocationCandidate>>?>(null) }
    var locationSelectionPrompt by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val sharedUri by incomingUri.collectAsState()

    LaunchedEffect(sharedUri) {
        sharedUri?.let {
            importUri = it
            onIncomingUriHandled()
        }
    }

    fun runImport(uri: Uri, resolution: DuplicateResolution = DuplicateResolution.ASK) {
        scope.launch {
            when (val result = graph.importService.import(uri, resolution)) {
                is ImportResult.Success -> {
                    duplicate = null
                    duplicateUri = null
                    if (result.locationCandidates.isNotEmpty()) {
                        locationConfirmation = result.planId to result.locationCandidates
                    } else if (result.unresolvedDestinationName != null) {
                        locationSelectionPrompt = result.planId to result.unresolvedDestinationName
                    }
                    navController.navigate("detail/${result.planId}")
                }
                is ImportResult.Duplicate -> {
                    duplicate = result
                    duplicateUri = uri
                }
                is ImportResult.Failure -> importMessage = result.message
                ImportResult.Cancelled -> duplicate = null
            }
        }
    }

    LaunchedEffect(importUri) {
        importUri?.let { uri ->
            runImport(uri)
            importUri = null
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in rootRoutes) {
                NavigationBar {
                    RootDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateRoot(destination.route) },
                            icon = {
                                Icon(
                                    if (selected) destination.selectedIcon else destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = RootDestination.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(RootDestination.HOME.route) {
                HomeScreen(plans, onOpenPlan = { navController.navigate("detail/$it") })
            }
            composable(RootDestination.LIBRARY.route) {
                PlanLibraryScreen(
                    plans = plans,
                    onImport = { uri -> importUri = uri },
                    onOpenPlan = { navController.navigate("detail/$it") },
                )
            }
            composable(RootDestination.PROFILE.route) { ProfileScreen(plans) }
            composable("detail/{planId}") { entry ->
                val planId = entry.arguments?.getString("planId")?.toLongOrNull()
                val plan = plans.firstOrNull { it.id == planId }
                if (plan != null) {
                    PlanDetailScreen(
                        plan = plan,
                        repository = graph.repository,
                        onBack = navController::popBackStack,
                        onEdit = { navController.navigate("edit/${plan.id}") },
                        onViewHtml = { original -> navController.navigate("web/${plan.id}/$original") },
                        onDeleted = {
                            navController.popBackStack()
                            navController.navigateRoot(RootDestination.LIBRARY.route)
                        },
                    )
                }
            }
            composable("edit/{planId}") { entry ->
                val planId = entry.arguments?.getString("planId")?.toLongOrNull()
                val plan = plans.firstOrNull { it.id == planId }
                if (plan != null) {
                    EditPlanScreen(plan, graph.repository, navController::popBackStack)
                }
            }
            composable("web/{planId}/{original}") { entry ->
                val planId = entry.arguments?.getString("planId")?.toLongOrNull()
                val original = entry.arguments?.getString("original").toBoolean()
                val plan = plans.firstOrNull { it.id == planId }
                if (plan != null) {
                    WebPlanScreen(plan, original, graph.repository, navController::popBackStack)
                }
            }
            composable("pick/{planId}") { entry ->
                val planId = entry.arguments?.getString("planId")?.toLongOrNull()
                val plan = plans.firstOrNull { it.id == planId }
                if (plan != null) {
                    LocationPickerScreen(plan, graph.repository) { navController.popBackStack() }
                }
            }
        }
    }

    duplicate?.let { existing ->
        AlertDialog(
            onDismissRequest = { duplicate = null },
            title = { Text("发现相同计划") },
            text = { Text("“${existing.existingTitle}”已在计划库中。") },
            confirmButton = {
                TextButton(onClick = {
                    duplicateUri?.let { runImport(it, DuplicateResolution.UPDATE) }
                    duplicate = null
                }) { Text("更新原计划") }
            },
            dismissButton = {
                TextButton(onClick = {
                    duplicateUri?.let { runImport(it, DuplicateResolution.KEEP_COPY) }
                    duplicate = null
                }) { Text("保留副本") }
            },
        )
    }
    importMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { importMessage = null },
            title = { Text("未能导入") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { importMessage = null }) { Text("知道了") } },
        )
    }
    locationConfirmation?.let { (planId, candidates) ->
        val candidate = candidates.first()
        AlertDialog(
            onDismissRequest = { locationConfirmation = null },
            title = { Text("确认地图位置") },
            text = {
                Text("${candidate.destinationName}\n${candidate.displayName}\n${"%.4f".format(candidate.latitude)}, ${"%.4f".format(candidate.longitude)}")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { graph.repository.confirmLocation(planId, candidate) }
                    locationConfirmation = null
                }) { Text("位置正确") }
            },
            dismissButton = {
                TextButton(onClick = { locationConfirmation = null }) { Text("暂不上地图") }
            },
        )
    }
    locationSelectionPrompt?.let { (planId, name) ->
        AlertDialog(
            onDismissRequest = { locationSelectionPrompt = null },
            title = { Text("没有找到“$name”") },
            text = { Text("可以暂不上地图，也可以在地图上手动点选位置。") },
            confirmButton = {
                TextButton(onClick = {
                    locationSelectionPrompt = null
                    navController.navigate("pick/$planId")
                }) { Text("地图点选") }
            },
            dismissButton = {
                TextButton(onClick = { locationSelectionPrompt = null }) { Text("稍后再说") }
            },
        )
    }
}

private fun NavHostController.navigateRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
