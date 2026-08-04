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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper
import com.lujian.travelplan.ui.theme.PaperDeep

private enum class GlobalGalleryMode { RECENT, BY_PLAN }

@Composable
fun GlobalGalleryScreen(
    plans: List<StoredPlan>,
    onOpenPlan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(GlobalGalleryMode.RECENT) }
    val recent = remember(plans) { GlobalGalleryPolicy.recent(plans) }
    val groups = remember(plans) { GlobalGalleryPolicy.byPlan(plans) }

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
                GlobalGalleryRow(row, onOpenPlan)
            }
        } else {
            items(groups, key = { it.planId }) { group ->
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        group.planTitle,
                        modifier = Modifier.clickable { onOpenPlan(group.planId) },
                        color = Coral,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    group.items.chunked(2).forEach { row -> GlobalGalleryRow(row, onOpenPlan) }
                }
            }
        }
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
private fun GlobalGalleryRow(items: List<GlobalGalleryItem>, onOpenPlan: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { galleryItem ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clickable { onOpenPlan(galleryItem.planId) },
            ) {
                PrivateGalleryImage(
                    relativePath = galleryItem.relativePath,
                    contentDescription = "${galleryItem.planTitle}照片",
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
            }
        }
        repeat(2 - items.size) { Box(Modifier.weight(1f)) }
    }
}
