package com.lujian.travelplan.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lujian.travelplan.data.PlanPhoto
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper
import com.lujian.travelplan.ui.theme.PaperDeep
import java.io.File

internal enum class PlanGalleryMode { RECENT, BY_PIN }

@Composable
internal fun PlanGalleryScreen(
    plan: StoredPlan,
    modifier: Modifier = Modifier,
    initialPinId: String? = null,
    onAddPhotos: (PhotoPin) -> Unit = {},
    onRemovePhoto: (PlanPhoto) -> Unit = {},
) {
    var mode by remember(initialPinId) {
        mutableStateOf(if (initialPinId == null) PlanGalleryMode.RECENT else PlanGalleryMode.BY_PIN)
    }
    var openedItem by remember { mutableStateOf<PlanGalleryItem?>(null) }
    var removeCandidate by remember { mutableStateOf<PlanPhoto?>(null) }
    val groups = remember(plan) { PlanGalleryPolicy.groups(plan) }
    val displayedGroups = remember(plan, groups, initialPinId) {
        if (initialPinId == null || groups.any { it.id == initialPinId }) {
            groups
        } else {
            val requestedPin = PlanGalleryPolicy.pins(plan).firstOrNull { it.id == initialPinId }
            if (requestedPin == null) groups else groups + PlanGalleryGroup(requestedPin.id, requestedPin.title, emptyList())
        }
    }
    val recent = remember(plan) { PlanGalleryPolicy.recent(plan) }
    val listState = rememberLazyListState()

    LaunchedEffect(initialPinId, displayedGroups, mode) {
        if (mode == PlanGalleryMode.BY_PIN && initialPinId != null) {
            displayedGroups.indexOfFirst { it.id == initialPinId }
                .takeIf { it >= 0 }
                ?.let { listState.animateScrollToItem(it + 1) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(Paper),
        contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 42.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "gallery-mode") {
            GalleryModeSelector(mode) { mode = it }
        }
        if (mode == PlanGalleryMode.RECENT && recent.isEmpty()) {
            item(key = "gallery-empty") {
                PaperCard(Modifier.fillMaxWidth(), background = PaperDeep) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("还没有旅途照片", style = MaterialTheme.typography.titleLarge)
                        Text("从行程或地图里的地点卡片添加照片。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else if (mode == PlanGalleryMode.RECENT) {
            items(recent.chunked(2), key = { row -> "recent-${row.joinToString { it.relativePath }}" }) { row ->
                GalleryRow(row, onOpen = { openedItem = it }, onRemove = { removeCandidate = it })
            }
        } else if (displayedGroups.isEmpty()) {
            item(key = "gallery-empty-by-pin") {
                PaperCard(Modifier.fillMaxWidth(), background = PaperDeep) {
                    Text("从行程或地图里的地点卡片添加照片。")
                }
            }
        } else {
            items(displayedGroups, key = { it.id }) { group ->
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(group.title, color = Coral, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (group.id != "cover") {
                            TextButton(onClick = { onAddPhotos(PhotoPin(group.id, group.title)) }) { Text("＋ 添加照片") }
                        }
                    }
                    if (group.items.isEmpty()) {
                        Text("这个地点还没有照片。", style = MaterialTheme.typography.bodyMedium)
                    }
                    group.items.chunked(2).forEach { row ->
                        GalleryRow(row, onOpen = { openedItem = it }, onRemove = { removeCandidate = it })
                    }
                }
            }
        }
    }

    openedItem?.let { item ->
        Dialog(onDismissRequest = { openedItem = null }) {
            PaperCard(Modifier.fillMaxWidth(), background = Ink, contentPadding = PaddingValues(4.dp)) {
                PrivateGalleryImage(
                    relativePath = item.relativePath,
                    contentDescription = "查看照片",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }
        }
    }
    removeCandidate?.let { photo ->
        AlertDialog(
            onDismissRequest = { removeCandidate = null },
            title = { Text("移除这张照片？") },
            text = { Text("只删除旅笺中的私有副本，不影响系统相册原图。") },
            confirmButton = {
                TextButton(onClick = { removeCandidate = null; onRemovePhoto(photo) }) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removeCandidate = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun GalleryModeSelector(mode: PlanGalleryMode, onSelect: (PlanGalleryMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            PlanGalleryMode.RECENT to "按加入时间",
            PlanGalleryMode.BY_PIN to "按大头针",
        ).forEach { (value, label) ->
            val selected = value == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) Ink else PaperDeep, RoundedCornerShape(999.dp))
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected) Paper else Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GalleryRow(
    items: List<PlanGalleryItem>,
    onOpen: (PlanGalleryItem) -> Unit,
    onRemove: (PlanPhoto) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            Box(Modifier.weight(1f)) {
                PrivateGalleryImage(
                    relativePath = item.relativePath,
                    contentDescription = if (item is PlanGalleryItem.Cover) "计划封面" else "地点照片",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onOpen(item) },
                )
                if (item is PlanGalleryItem.Cover) {
                    Text(
                        "封面",
                        modifier = Modifier.align(Alignment.BottomStart).background(Ink.copy(alpha = .75f)).padding(7.dp),
                        color = Paper,
                        style = MaterialTheme.typography.labelSmall,
                    )
                } else if (item is PlanGalleryItem.Photo) {
                    IconButton(
                        onClick = { onRemove(item.value) },
                        modifier = Modifier.align(Alignment.TopEnd).size(38.dp).background(Ink.copy(alpha = .68f)),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "移除照片", tint = Color.White)
                    }
                }
            }
        }
        repeat(2 - items.size) { Box(Modifier.weight(1f)) }
    }
}

@Composable
internal fun PrivateGalleryImage(
    relativePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = relativePath) {
        value = PlanThumbnailLoader.decode(File(context.filesDir, relativePath))
    }
    Box(modifier.background(PaperDeep), contentAlignment = Alignment.Center) {
        if (bitmap == null) {
            Icon(Icons.Filled.Image, contentDescription = contentDescription, tint = Ink.copy(alpha = .35f))
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
