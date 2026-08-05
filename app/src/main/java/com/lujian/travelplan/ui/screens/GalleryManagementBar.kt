package com.lujian.travelplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun GalleryManagementBar(
    managing: Boolean,
    availableKeys: Set<GallerySelectionKey>,
    selectedKeys: Set<GallerySelectionKey>,
    onManagingChange: (Boolean) -> Unit,
    onSelectionChange: (Set<GallerySelectionKey>) -> Unit,
    onConfirmDelete: (Set<GallerySelectionKey>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmationOpen by remember { mutableStateOf(false) }

    if (!managing) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(
                enabled = availableKeys.isNotEmpty(),
                onClick = { onManagingChange(true) },
            ) {
                Text("管理")
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    onSelectionChange(
                        if (selectedKeys.size == availableKeys.size) emptySet()
                        else GallerySelectionPolicy.selectAll(availableKeys),
                    )
                },
            ) {
                Text(if (selectedKeys.size == availableKeys.size) "取消全选" else "全选")
            }
            Text("已选 ${selectedKeys.size} 项", style = MaterialTheme.typography.labelLarge)
            TextButton(
                enabled = selectedKeys.isNotEmpty(),
                onClick = { confirmationOpen = true },
            ) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
            TextButton(
                onClick = {
                    onSelectionChange(emptySet())
                    onManagingChange(false)
                },
            ) {
                Text("完成")
            }
        }
    }

    if (confirmationOpen) {
        AlertDialog(
            onDismissRequest = { confirmationOpen = false },
            title = { Text(GallerySelectionPolicy.summary(selectedKeys).confirmation) },
            text = { Text("只删除旅笺中的私有副本，不影响系统相册原图。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmationOpen = false
                        onConfirmDelete(selectedKeys)
                    },
                ) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmationOpen = false }) { Text("取消") }
            },
        )
    }
}
