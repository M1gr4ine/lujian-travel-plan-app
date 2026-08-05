@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.lujian.travelplan.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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
import com.lujian.travelplan.ui.screens.GlobalGalleryScreen
import com.lujian.travelplan.ui.screens.LocationPickerScreen
import com.lujian.travelplan.ui.screens.PlanDetailScreen
import com.lujian.travelplan.ui.screens.PlanLibraryScreen
import com.lujian.travelplan.ui.screens.ProfileScreen
import com.lujian.travelplan.ui.screens.WebPlanScreen
import com.lujian.travelplan.ui.theme.Gold
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper
import com.lujian.travelplan.ui.theme.PaperDeep
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class RootDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME("home", "首页", Icons.Outlined.Home, Icons.Rounded.Home),
    LIBRARY("library", "旅笺板", Icons.Outlined.Luggage, Icons.Rounded.Luggage),
    GALLERY("gallery", "相册", Icons.Outlined.PhotoLibrary, Icons.Rounded.PhotoLibrary),
    PROFILE("profile", "我", Icons.Outlined.AccountCircle, Icons.Rounded.AccountCircle),
}

private val SmoothPageEasing = CubicBezierEasing(.22f, 1f, .36f, 1f)

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
    LujianApp(graph, incomingUri, reduceMotion, onIncomingUriHandled)
}

@Composable
private fun LujianApp(
    graph: AppGraph,
    incomingUri: StateFlow<Uri?>,
    reduceMotion: Boolean,
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
    var homeMapDragEnabled by remember { mutableStateOf(false) }
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Paper)
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    RootNavigationBar(
                        currentRoute = currentRoute,
                        reduceMotion = reduceMotion,
                        modifier = Modifier
                            .fillMaxWidth(.88f)
                            .clip(RoundedCornerShape(28.dp))
                            .border(3.dp, Ink, RoundedCornerShape(28.dp)),
                        onSelect = { navController.navigateRoot(it.route) },
                    )
                }
            }
        },
    ) { padding ->
        SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = RootDestination.HOME.route,
            modifier = Modifier
                .padding(padding)
                .pointerInput(currentRoute, homeMapDragEnabled) {
                    val currentIndex = RootDestination.entries.indexOfFirst { it.route == currentRoute }
                    if (currentIndex < 0) return@pointerInput
                    val threshold = 56.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var totalX = 0f
                        var totalY = 0f
                        var pressed = true
                        var switched = false
                        var childConsumed = false
                        while (pressed && !switched) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.position - change.previousPosition
                            totalX += delta.x
                            totalY += delta.y
                            childConsumed = childConsumed || change.isConsumed
                            pressed = change.pressed
                            RootTabSwipePolicy.directionForGesture(
                                totalX = totalX,
                                totalY = totalY,
                                threshold = threshold,
                                childConsumed = childConsumed,
                                allowConsumedGesture = currentRoute == RootDestination.HOME.route && !homeMapDragEnabled,
                            )?.let { direction ->
                                RootTabSwipePolicy.adjacentIndex(
                                    currentIndex = currentIndex,
                                    direction = direction,
                                    count = RootDestination.entries.size,
                                )?.let { targetIndex ->
                                    switched = true
                                    navController.navigateRoot(RootDestination.entries[targetIndex].route)
                                }
                            }
                        }
                    }
                },
            enterTransition = {
                if (reduceMotion || isLibraryDetailTransition(initialState.destination.route, targetState.destination.route)) {
                    EnterTransition.None
                } else {
                    val direction = rootDirection(initialState.destination.route, targetState.destination.route)
                    fadeIn(tween(220, delayMillis = 20, easing = SmoothPageEasing)) +
                        slideInHorizontally(tween(320, easing = SmoothPageEasing)) { width ->
                            direction * width / 4
                        }
                }
            },
            exitTransition = {
                if (reduceMotion || isLibraryDetailTransition(initialState.destination.route, targetState.destination.route)) {
                    ExitTransition.None
                } else {
                    val direction = rootDirection(initialState.destination.route, targetState.destination.route)
                    fadeOut(tween(180, easing = SmoothPageEasing)) +
                        slideOutHorizontally(tween(280, easing = SmoothPageEasing)) { width ->
                            -direction * width / 6
                        }
                }
            },
            popEnterTransition = {
                if (reduceMotion || isLibraryDetailTransition(initialState.destination.route, targetState.destination.route)) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(220, delayMillis = 20, easing = SmoothPageEasing)) +
                        slideInHorizontally(tween(320, easing = SmoothPageEasing)) { width -> -width / 4 }
                }
            },
            popExitTransition = {
                if (reduceMotion || isLibraryDetailTransition(initialState.destination.route, targetState.destination.route)) {
                    ExitTransition.None
                } else {
                    fadeOut(tween(180, easing = SmoothPageEasing)) +
                        slideOutHorizontally(tween(280, easing = SmoothPageEasing)) { width -> width / 6 }
                }
            },
        ) {
            composable(RootDestination.HOME.route) {
                HomeScreen(
                    plans = plans,
                    onOpenPlan = { navController.navigate("detail/$it") },
                    onDragEnabledChange = { homeMapDragEnabled = it },
                )
            }
            composable(RootDestination.LIBRARY.route) {
                PlanLibraryScreen(
                    plans = plans,
                    onImport = { uri -> importUri = uri },
                    onOpenPlan = { planId ->
                        navController.navigate("detail/$planId") { launchSingleTop = true }
                    },
                    onDeletePlans = { ids -> scope.launch { graph.repository.deleteAll(ids) } },
                    onSetArchived = { ids, archived ->
                        scope.launch { graph.repository.setArchived(ids, archived) }
                    },
                    reduceMotion = reduceMotion,
                    transitionScopes = PlanSharedTransitionScopes(this@SharedTransitionLayout, this),
                    sharedBoundsEnabled = !reduceMotion,
                )
            }
            composable(RootDestination.GALLERY.route) {
                GlobalGalleryScreen(
                    plans = plans,
                    onOpenPlan = { planId -> navController.navigate("detail/$planId") },
                    onDeleteItems = graph.repository::removeGalleryItems,
                )
            }
            composable(RootDestination.PROFILE.route) { ProfileScreen(plans) }
            composable("detail/{planId}") { entry ->
                val planId = entry.arguments?.getString("planId")?.toLongOrNull()
                val plan = plans.firstOrNull { it.id == planId }
                if (plan != null) {
                    val useSharedBounds = rememberPlanNoteSharedBoundsEnabled(
                        entryKey = entry,
                        fromRoute = navController.previousBackStackEntry?.destination?.route,
                        reduceMotion = reduceMotion,
                    )
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
                        transitionScopes = PlanSharedTransitionScopes(this@SharedTransitionLayout, this),
                        sharedBoundsEnabled = useSharedBounds,
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
    }

    duplicate?.let { existing ->
        AlertDialog(
            onDismissRequest = { duplicate = null },
            title = { Text("发现相同计划") },
            text = { Text("“${existing.existingTitle}”已在旅笺板中。") },
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

@Composable
private fun RootNavigationBar(
    currentRoute: String?,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (RootDestination) -> Unit,
) {
    val selectedIndex = RootDestination.entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    BoxWithConstraints(modifier = modifier.height(80.dp).background(PaperDeep)) {
        val itemWidth = maxWidth / RootDestination.entries.size
        val indicatorWidth = 72.dp
        val targetX = itemWidth * selectedIndex + (itemWidth - indicatorWidth) / 2
        val indicatorX by animateDpAsState(
            targetValue = targetX,
            animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = .8f, stiffness = 280f),
            label = "底栏指示块位置",
        )
        Box(
            Modifier
                .offset(x = indicatorX, y = 8.dp)
                .width(indicatorWidth)
                .height(40.dp)
                .background(Color(0xFFE8DDF2), RoundedCornerShape(22.dp)),
        )
        NavigationBar(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            RootDestination.entries.forEach { destination ->
                val selected = currentRoute == destination.route
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else .96f,
                    animationSpec = if (reduceMotion) tween(0) else tween(260, easing = SmoothPageEasing),
                    label = "${destination.label}图标缩放",
                )
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Ink,
                        selectedTextColor = Gold,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = Ink,
                        unselectedTextColor = Ink,
                    ),
                    icon = {
                        Crossfade(
                            targetState = selected,
                            animationSpec = if (reduceMotion) tween(0) else tween(150),
                            label = "${destination.label}图标切换",
                        ) { isSelected ->
                            Icon(
                                if (isSelected) destination.selectedIcon else destination.icon,
                                contentDescription = destination.label,
                                modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                            )
                        }
                    },
                    label = { Text(destination.label) },
                )
            }
        }
    }
}

private fun rootDirection(initialRoute: String?, targetRoute: String?): Int {
    val initialIndex = RootDestination.entries.indexOfFirst { it.route == initialRoute }
    val targetIndex = RootDestination.entries.indexOfFirst { it.route == targetRoute }
    return if (initialIndex >= 0 && targetIndex >= 0 && targetIndex < initialIndex) -1 else 1
}

private fun isLibraryDetailTransition(initialRoute: String?, targetRoute: String?): Boolean =
    (initialRoute == RootDestination.LIBRARY.route && targetRoute == "detail/{planId}") ||
        (initialRoute == "detail/{planId}" && targetRoute == RootDestination.LIBRARY.route)

private fun NavHostController.navigateRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
