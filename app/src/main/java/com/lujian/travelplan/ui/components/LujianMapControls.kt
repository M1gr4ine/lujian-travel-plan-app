package com.lujian.travelplan.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper

@Composable
fun LujianMapControls(
    dragEnabled: Boolean,
    enabled: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleDrag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Paper.copy(alpha = .94f))
            .border(BorderStroke(1.dp, Ink.copy(alpha = .18f)), RoundedCornerShape(10.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MapControlButton("+", "放大地图", enabled, false, 36, onZoomIn)
        MapControlButton("−", "缩小地图", enabled, false, 36, onZoomOut)
        MapControlButton(if (dragEnabled) "DRAG ON" else "DRAG", "拖动地图锁定", enabled, dragEnabled, 70, onToggleDrag)
    }
}

@Composable
private fun MapControlButton(
    label: String,
    description: String,
    enabled: Boolean,
    active: Boolean,
    width: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier
            .width(width.dp)
            .height(34.dp)
            .alpha(if (enabled) 1f else .42f)
            .clip(shape)
            .background(if (active) Coral else Color.Transparent)
            .border(1.dp, if (active) Coral else Ink, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else Ink,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
        )
    }
}
