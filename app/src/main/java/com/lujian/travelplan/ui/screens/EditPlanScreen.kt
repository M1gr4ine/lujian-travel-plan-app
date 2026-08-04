@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lujian.travelplan.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.data.PlanRepository
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanItemDraft
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.theme.Gold
import kotlinx.coroutines.launch

@Composable
fun EditPlanScreen(
    plan: StoredPlan,
    repository: PlanRepository,
    onBack: () -> Unit,
) {
    var draft by remember(plan.updatedAt) { mutableStateOf(plan.parsed) }
    var saving by remember { mutableStateOf(false) }
    var coverMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            coverMessage = repository.setCustomCover(plan.id, uri).exceptionOrNull()?.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑计划") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    TextButton(
                        enabled = !saving && draft.title.isNotBlank(),
                        onClick = {
                            saving = true
                            scope.launch {
                                repository.saveEdits(plan.id, draft)
                                saving = false
                                onBack()
                            }
                        },
                    ) { Text(if (saving) "保存中" else "保存") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 10.dp, 16.dp, 48.dp),
        ) {
            item {
                CoverEditorCard(
                    customCoverPath = plan.customCoverPath,
                    thumbnailPath = plan.thumbnailPath,
                    onChoose = {
                        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onClear = {
                        scope.launch {
                            coverMessage = repository.clearCustomCover(plan.id).exceptionOrNull()?.message
                        }
                    },
                )
            }
            coverMessage?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            item {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    label = { Text("计划名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            if (draft.destinations.isNotEmpty()) {
                item {
                    val destination = draft.destinations.first()
                    OutlinedTextField(
                        value = destination.name,
                        onValueChange = { value ->
                            draft = draft.copy(
                                destinations = draft.destinations.toMutableList().also {
                                    it[0] = destination.copy(name = value)
                                },
                            )
                        },
                        label = { Text("目的地") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
            itemsIndexed(draft.days, key = { _, day -> day.id }) { dayIndex, day ->
                DayEditor(
                    day = day,
                    dayIndex = dayIndex,
                    onChange = { changed -> draft = draft.replaceDay(dayIndex, changed) },
                )
            }
        }
    }
}

@Composable
private fun DayEditor(
    day: PlanDayDraft,
    dayIndex: Int,
    onChange: (PlanDayDraft) -> Unit,
) {
    PaperCard(Modifier.fillMaxWidth(), background = Gold.copy(alpha = .20f)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("DAY ${dayIndex + 1}", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = day.label,
                    onValueChange = { onChange(day.copy(label = it)) },
                    label = { Text("日期") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = day.title,
                    onValueChange = { onChange(day.copy(title = it)) },
                    label = { Text("每日标题") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            day.items.forEachIndexed { index, item ->
                ItemEditor(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < day.items.lastIndex,
                    onChange = { changed -> onChange(day.replaceItem(index, changed)) },
                    onDelete = { onChange(day.copy(items = day.items.filterIndexed { i, _ -> i != index })) },
                    onMoveUp = { onChange(day.moveItem(index, index - 1)) },
                    onMoveDown = { onChange(day.moveItem(index, index + 1)) },
                )
            }
            Button(onClick = {
                val item = PlanItemDraft(
                    id = "item-${System.nanoTime()}",
                    time = "",
                    title = "新行程",
                    category = "",
                    cost = "",
                    notes = "",
                )
                onChange(day.copy(items = day.items + item))
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("添加行程")
            }
        }
    }
}

@Composable
private fun ItemEditor(
    item: PlanItemDraft,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (PlanItemDraft) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    PaperCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text("行程项", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Filled.KeyboardArrowUp, "上移") }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Filled.KeyboardArrowDown, "下移") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.time.orEmpty(),
                    onValueChange = { onChange(item.copy(time = it)) },
                    label = { Text("时间") },
                    modifier = Modifier.weight(.75f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = item.title,
                    onValueChange = { onChange(item.copy(title = it)) },
                    label = { Text("标题") },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.category.orEmpty(),
                    onValueChange = { onChange(item.copy(category = it)) },
                    label = { Text("类别") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = item.cost.orEmpty(),
                    onValueChange = { onChange(item.copy(cost = it)) },
                    label = { Text("费用") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = item.notes.orEmpty(),
                onValueChange = { onChange(item.copy(notes = it)) },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }
}

private fun com.lujian.travelplan.model.ParsedPlan.replaceDay(index: Int, day: PlanDayDraft) =
    copy(days = days.toMutableList().also { it[index] = day })

private fun PlanDayDraft.replaceItem(index: Int, item: PlanItemDraft) =
    copy(items = items.toMutableList().also { it[index] = item })

private fun PlanDayDraft.moveItem(from: Int, to: Int): PlanDayDraft {
    if (to !in items.indices) return this
    return copy(items = items.toMutableList().also { list -> list.add(to, list.removeAt(from)) })
}
