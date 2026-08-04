package com.lujian.travelplan.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.theme.PaperDeep
import java.io.File

@Composable
internal fun CoverEditorCard(
    customCoverPath: String?,
    thumbnailPath: String?,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewPath = customCoverPath ?: thumbnailPath
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = previewPath) {
        value = previewPath?.let { PlanThumbnailLoader.decode(File(context.filesDir, it)) }
    }

    PaperCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("计划预览图", style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(PaperDeep),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "当前计划预览图",
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Filled.Image, contentDescription = null)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (customCoverPath != null) {
                    TextButton(onClick = onClear) { Text("恢复默认") }
                }
                TextButton(onClick = onChoose) {
                    Text(if (customCoverPath == null) "选择预览图" else "更换预览图")
                }
            }
        }
    }
}
