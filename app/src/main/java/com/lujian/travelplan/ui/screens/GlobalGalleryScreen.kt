package com.lujian.travelplan.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.data.GalleryDeleteRequest
import com.lujian.travelplan.data.GalleryDeleteResult
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper
import com.lujian.travelplan.ui.theme.PaperDeep
import kotlinx.coroutines.launch

private enum class GlobalGalleryMode { RECENT, BY_PLAN }

@Composable
fun GlobalGalleryScreen(
    plans: List<StoredPlan>,
    onOpenPlan: (Long) -> Unit,
    onDeleteItems: suspend (GalleryDeleteRequest) -> Result<GalleryDeleteResult> = {
        Result.success(GalleryDeleteResult(0, 0))
    },
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(GlobalGalleryMode.RECENT) }
    var managing by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<GallerySelectionKey>()) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val recent = remember(plans) { GlobalGalleryPolicy.recent(plans) }
    val groups = remember(plans) { GlobalGalleryPolicy.byPlan(plans) }
    val availableKeys = remember(recent) {
        recent.mapTo(mutableSetOf()) { it.item.selectionKey(it.planId) }
    }

    LaunchedEffect(availableKeys) {
        selectedKeys = GallerySelectionPolicy.retainAvailable(selectedKeys, availableKeys)
        if (availableKeys.isEmpty()) managing = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Paper),
        contentPadding = PaddingValues(18.dp, 22.dp, 18.dp, 42.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "gallery-title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("相册", style = MaterialTheme.typography.headlineLarge)
                Text("每一次出发，都在这里留下光。", color = Coral, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item(key = "gallery-management") {
            GalleryManagementBar(
                managing = managing,
                availableKeys = availableKeys,
                selectedKeys = selectedKeys,
                onManagingChange = { managing = it },
                onSelectionChange = { selectedKeys = it },
                onConfirmDelete = { keys ->
                    scope.launch {
                        onDeleteItems(keys.toDeleteRequest()).fold(
                            onSuccess = {
                                selectedKeys = emptySet()
                                managing = false
                            },
                            onFailure = { error -> deleteError = error.message ?: "批量删除失败" },
                        )
                    }
                },
            )
        }
        item(key = "gallery-mode") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GalleryModeButton("按加入时间", mode == GlobalGalleryMode.RECENT, Modifier.weight(1f)) {
                    mode = GlobalGalleryMode.RECENT
                }
                GalleryModeButton("按计划", mode == GlobalGalleryMode.BY_PLAN, Modifier.weight(1f)) {
                    mode = GlobalGalleryMode.BY_PLAN
                }
            }
        }
        if (recent.isEmpty()) {
            item(key = "gallery-empty") {
                PaperCard(Modifier.fillMaxWidth(), background = PaperDeep) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("相册还是空的", style = MaterialTheme.typography.titleLarge)
                        Text("进入计划，在行程或地图地点中添加照片。")
                    }
                }
            }
        } else if (mode == GlobalGalleryMode.RECENT) {
            items(recent.chunked(2), key = { row -> "global-${row.joinToString { it.relativePath }}" }) { row ->
                GlobalGalleryRow(
                    items = row,
                    managing = managing,
                    selectedKeys = selectedKeys,
                    onToggle = { key ->
                        selectedKeys = selectedKeys.toMutableSet().apply {
                            if (!add(key)) remove(key)
                        }
                    },
                    onOpenPlan = onOpenPlan,
                )
            }
        } else {
            items(groups, key = { it.planId }) { group ->
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        group.planTitle,
                        modifier = Modifier.clickable(enabled = !managing) { onOpenPlan(group.planId) },
                        color = Coral,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    group.items.chunked(2).forEach { row ->
                        GlobalGalleryRow(
                            items = row,
                            managing = managing,
                            selectedKeys = selectedKeys,
                            onToggle = { key ->
                                selectedKeys = selectedKeys.toMutableSet().apply {
                                    if (!add(key)) remove(key)
                                }
                            },
                            onOpenPlan = onOpenPlan,
                        )
                    }
                }
            }
        }
    }

    deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            title = { Text("批量删除失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text("知道了") } },
        )
    }
}

@Composable
private fun GalleryModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(if (selected) Ink else PaperDeep, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Paper else Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GlobalGalleryRow(
    items: List<GlobalGalleryItem>,
    managing: Boolean,
    selectedKeys: Set<GallerySelectionKey>,
    onToggle: (GallerySelectionKey) -> Unit,
    onOpenPlan: (Long) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { galleryItem ->
            val key = galleryItem.item.selectionKey(galleryItem.planId)
            val selected = key in selectedKeys
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clickable {
                        if (managing) onToggle(key) else onOpenPlan(galleryItem.planId)
                    },
            ) {
                PrivateGalleryImage(
                    relativePath = galleryItem.relativePath,
                    contentDescription = when (galleryItem.item) {
                        is PlanGalleryItem.Cover -> "${galleryItem.planTitle}自定义预览图"
                        is PlanGalleryItem.Photo -> "${galleryItem.planTitle}地点照片"
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    galleryItem.planTitle,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Ink.copy(alpha = .72f))
                        .padding(8.dp),
                    color = Paper,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (managing) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (selected) "已选择" else "未选择",
                        tint = if (selected) Coral else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(38.dp)
                            .background(Ink.copy(alpha = .68f), RoundedCornerShape(999.dp))
                            .padding(5.dp),
                    )
                }
            }
        }
        repeat(2 - items.size) { Box(Modifier.weight(1f)) }
    }
}
