package com.lujian.travelplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Gold
import com.lujian.travelplan.ui.theme.Paper

@Composable
fun ProfileScreen(plans: List<StoredPlan>) {
    val destinations = plans.flatMap { it.parsed.destinations }.map { it.name }.distinct().size
    val days = plans.sumOf { it.parsed.days.size }
    Column(
        Modifier.fillMaxSize().background(Paper).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = "头像占位",
                tint = Coral,
                modifier = Modifier.padding(end = 14.dp),
            )
            Column {
                Text("旅行者", style = MaterialTheme.typography.headlineMedium)
                Text("所有计划只保存在这台手机", style = MaterialTheme.typography.bodyMedium)
            }
        }
        PaperCard(modifier = Modifier.fillMaxWidth(), background = Gold.copy(alpha = .42f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Stat("计划", plans.size)
                Stat("目的地", destinations)
                Stat("旅行天数", days)
            }
        }
        Text("设置", style = MaterialTheme.typography.titleLarge)
        SettingRow(Icons.Filled.Security, "隐私与安全", "HTML 隔离阅读已开启")
        SettingRow(Icons.Filled.CloudOff, "离线计划", "地图以外均可离线使用")
        SettingRow(Icons.Filled.Palette, "纸张主题", "固定浅色 · 手账风格")
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Coral)
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
