package com.lujian.travelplan.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.ui.PlanSharedTransitionScopes
import com.lujian.travelplan.ui.components.dashedPaperBorder
import com.lujian.travelplan.ui.components.foldedNoteDecoration
import com.lujian.travelplan.ui.planSharedBounds
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper
import com.lujian.travelplan.ui.theme.PaperDeep
import java.io.File

@Composable
fun PlanLibraryScreen(
    plans: List<StoredPlan>,
    onImport: (Uri) -> Unit,
    onOpenPlan: (Long) -> Unit,
    onDeletePlans: (Set<Long>) -> Unit = {},
    onSetArchived: (Set<Long>, Boolean) -> Unit = { _, _ -> },
    reduceMotion: Boolean = false,
    transitionScopes: PlanSharedTransitionScopes? = null,
    sharedBoundsEnabled: Boolean = false,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    var managing by remember { mutableStateOf(false) }
    var board by remember { mutableStateOf(TravelBoard.PLANS) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingDelete by remember { mutableStateOf<Set<Long>?>(null) }
    val boardPlans = remember(board, plans) { TravelBoardPolicy.plansFor(board, plans) }
    val planIds = remember(boardPlans) { boardPlans.map { it.id } }
    val planGridState = rememberLazyGridState()
    val footprintGridState = rememberLazyGridState()

    LaunchedEffect(board, planIds) {
        selectedIds = selectedIds.intersect(planIds.toSet())
        if (planIds.isEmpty()) managing = false
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable {
                        board = if (board == TravelBoard.PLANS) TravelBoard.FOOTPRINTS else TravelBoard.PLANS
                        selectedIds = emptySet()
                        managing = false
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(board.title, style = MaterialTheme.typography.headlineLarge)
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = "切换计划板和足迹板",
                        tint = Coral,
                        modifier = Modifier.padding(start = 6.dp).size(24.dp),
                    )
                }
                Text(board.subtitle, color = Coral, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                enabled = boardPlans.isNotEmpty(),
                onClick = {
                    managing = !managing
                    selectedIds = emptySet()
                },
            ) {
                Text(if (managing) "完成" else "管理", fontWeight = FontWeight.Bold)
            }
        }
        AnimatedVisibility(visible = managing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .background(PaperDeep, RoundedCornerShape(18.dp))
                    .border(2.dp, Ink, RoundedCornerShape(18.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { selectedIds = PlanSelectionPolicy.toggleAll(selectedIds, planIds) }) {
                    Text(if (planIds.isNotEmpty() && selectedIds.containsAll(planIds)) "取消全选" else "全选")
                }
                Text("已选 ${selectedIds.size} 项", color = Ink.copy(alpha = .62f), fontWeight = FontWeight.Bold)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        onSetArchived(selectedIds, TravelBoardPolicy.archiveValue(board))
                        selectedIds = emptySet()
                        managing = false
                    },
                ) {
                    Icon(
                        if (board == TravelBoard.PLANS) Icons.Filled.Archive else Icons.Filled.Unarchive,
                        contentDescription = null,
                    )
                    Text(if (board == TravelBoard.PLANS) "归档" else "移回计划板")
                }
                TextButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = { pendingDelete = selectedIds },
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("删除")
                }
            }
        }
        AnimatedContent(
            targetState = board,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(90)) togetherWith fadeOut(tween(90))
                } else {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(280)) { width -> direction * width / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(280)) { width -> -direction * width / 3 } + fadeOut(tween(180)))
                }
            },
            label = "旅笺板切换",
        ) { visibleBoard ->
            val visiblePlans = TravelBoardPolicy.plansFor(visibleBoard, plans)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = if (visibleBoard == TravelBoard.PLANS) planGridState else footprintGridState,
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (visibleBoard == TravelBoard.PLANS) {
                    item(key = "add") {
                        AddPlanCard {
                            launcher.launch(arrayOf("text/html", "application/xhtml+xml", "application/octet-stream"))
                        }
                    }
                }
                if (visiblePlans.isEmpty() && visibleBoard == TravelBoard.FOOTPRINTS) {
                    item(key = "empty-footprints", span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("走过的旅程会收在这里", style = MaterialTheme.typography.titleLarge)
                            Text("从计划板的管理模式归档旅程", color = Ink.copy(alpha = .58f))
                        }
                    }
                }
                items(visiblePlans, key = { it.id }) { plan ->
                    PlanPreviewCard(
                        plan = plan,
                        managing = managing,
                        selected = plan.id in selectedIds,
                        transitionScopes = transitionScopes,
                        sharedBoundsEnabled = sharedBoundsEnabled,
                        modifier = Modifier.animateItem(),
                        onClick = {
                            if (managing) {
                                selectedIds = PlanSelectionPolicy.toggle(selectedIds, plan.id)
                            } else {
                                onOpenPlan(plan.id)
                            }
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除 ${ids.size} 份计划？") },
            text = { Text("计划文件、编辑内容和缩略图将从本机移除。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlans(ids)
                    selectedIds = emptySet()
                    pendingDelete = null
                    if (ids.size == boardPlans.size) managing = false
                }) { Text("删除", color = Coral, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AddPlanCard(onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .dashedPaperBorder()
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Add, contentDescription = "添加计划", tint = Coral)
            Text("添加计划", style = MaterialTheme.typography.titleLarge)
            Text("选择 HTML 文件", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlanPreviewCard(
    plan: StoredPlan,
    managing: Boolean,
    selected: Boolean,
    transitionScopes: PlanSharedTransitionScopes?,
    sharedBoundsEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = plan.thumbnailPath) {
        value = plan.thumbnailPath?.let { PlanThumbnailLoader.decode(File(context.filesDir, it)) }
    }
    val scale by animateFloatAsState(if (selected) .96f else 1f, label = "计划卡选择缩放")
    val destination = plan.parsed.destinations.joinToString(" · ") { it.name }.ifBlank { "未设置目的地" }
    val dayLabels = plan.parsed.days.map { it.label }.filter { it.isNotBlank() }
    val date = when {
        dayLabels.isEmpty() -> "日期待定"
        dayLabels.size == 1 -> dayLabels.first()
        else -> "${dayLabels.first()} – ${dayLabels.last()}"
    }
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics(mergeDescendants = true) {
                contentDescription = "${plan.parsed.title}折角便签"
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .planSharedBounds(plan.id, transitionScopes, sharedBoundsEnabled)
                .foldedNoteDecoration(
                    background = PaperDeep,
                    borderColor = if (selected) Coral else Ink,
                    borderWidth = if (selected) 4.dp else 3.dp,
                )
                .padding(7.dp),
        ) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Paper),
                contentAlignment = Alignment.Center,
            ) {
                val currentBitmap = bitmap
                if (currentBitmap != null) {
                    Image(
                        currentBitmap.asImageBitmap(),
                        contentDescription = "${plan.parsed.title}内容缩略图",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        if (plan.parsed.capability == PlanCapability.ENHANCED) Icons.Filled.Map else Icons.Filled.Description,
                        contentDescription = null,
                        tint = Coral,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }

            if (managing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp, start = 8.dp)
                        .size(24.dp)
                        .background(if (selected) Coral else Paper, RoundedCornerShape(8.dp))
                        .border(2.dp, Ink, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    SelectionCheck(visible = selected)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            val metadataStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
            Text(
                plan.parsed.title,
                color = Ink.copy(alpha = .72f),
                style = metadataStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(destination, color = Ink.copy(alpha = .56f), style = metadataStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(date, color = Ink.copy(alpha = .56f), style = metadataStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SelectionCheck(visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = fadeIn() + scaleIn()) {
        Icon(Icons.Filled.Check, contentDescription = "已选择", tint = Ink, modifier = Modifier.size(16.dp))
    }
}
