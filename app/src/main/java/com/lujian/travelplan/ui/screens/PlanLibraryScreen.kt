package com.lujian.travelplan.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.components.dashedPaperBorder
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Gold
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Mint
import com.lujian.travelplan.ui.theme.Paper
import java.io.File

@Composable
fun PlanLibraryScreen(
    plans: List<StoredPlan>,
    onImport: (Uri) -> Unit,
    onOpenPlan: (Long) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    Column(Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text("计划库", style = MaterialTheme.typography.headlineLarge)
            Text("把每一次出发，装进一张卡片", color = Coral, style = MaterialTheme.typography.labelLarge)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "add") {
                AddPlanCard {
                    launcher.launch(arrayOf("text/html", "application/xhtml+xml", "application/octet-stream"))
                }
            }
            items(plans, key = { it.id }) { plan ->
                PlanPreviewCard(plan, onClick = { onOpenPlan(plan.id) })
            }
        }
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
private fun PlanPreviewCard(plan: StoredPlan, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(plan.thumbnailPath) {
        plan.thumbnailPath?.let { BitmapFactory.decodeFile(File(context.filesDir, it).absolutePath) }
    }
    PaperCard(
        modifier = Modifier.aspectRatio(1f).clickable(onClick = onClick),
        background = if (plan.id % 2L == 0L) Gold.copy(alpha = .42f) else Mint.copy(alpha = .35f),
        contentPadding = PaddingValues(0.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = plan.parsed.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Ink.copy(alpha = .82f)).padding(10.dp)) {
                Text(plan.parsed.title, color = Paper, style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(
                    if (plan.parsed.capability == PlanCapability.ENHANCED) Icons.Filled.Map else Icons.Filled.Description,
                    contentDescription = null,
                    tint = Coral,
                )
                Column {
                    Text(plan.parsed.title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(
                        plan.parsed.destinations.joinToString(" · ") { it.name }.ifBlank { "未设置目的地" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                    Text(
                        if (plan.parsed.capability == PlanCapability.ENHANCED) "增强计划" else "仅查看",
                        color = Coral,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
