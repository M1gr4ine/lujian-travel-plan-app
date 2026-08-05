@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lujian.travelplan.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.data.PlanRepository
import com.lujian.travelplan.data.PlanPhoto
import com.lujian.travelplan.data.GalleryDeleteRequest
import com.lujian.travelplan.data.GalleryDeleteResult
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.export.MobileHtmlGenerator
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanItemDraft
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanSectionDraft
import com.lujian.travelplan.ui.PlanSharedTransitionScopes
import com.lujian.travelplan.ui.components.PaperCard
import com.lujian.travelplan.ui.planSharedBounds
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Gold
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Mint
import com.lujian.travelplan.ui.theme.Paper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlanDetailScreen(
    plan: StoredPlan,
    repository: PlanRepository,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onViewHtml: (Boolean) -> Unit,
    onDeleted: () -> Unit,
    transitionScopes: PlanSharedTransitionScopes? = null,
    sharedBoundsEnabled: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var photoPin by remember { mutableStateOf<PhotoPin?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(50),
    ) { uris ->
        val pin = photoPin
        photoPin = null
        if (pin != null && uris.isNotEmpty()) {
            scope.launch {
                photoError = repository.addPhotos(plan.id, pin.id, pin.title, uris).exceptionOrNull()?.message
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri ->
        uri?.let { outputUri ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(outputUri)?.use { output ->
                        if (plan.parsed.capability == PlanCapability.ENHANCED) {
                            output.write(MobileHtmlGenerator.generate(plan.parsed).toByteArray(Charsets.UTF_8))
                        } else {
                            repository.htmlFile(plan, original = true).inputStream().use { it.copyTo(output) }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .planSharedBounds(plan.id, transitionScopes, sharedBoundsEnabled),
        topBar = {
            TopAppBar(
                title = {
                    Text(plan.parsed.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    if (plan.parsed.capability == PlanCapability.ENHANCED) {
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "编辑计划") }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("查看原始 HTML") },
                            leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) },
                            onClick = { menuOpen = false; onViewHtml(true) },
                        )
                        if (plan.generatedPath != null) {
                            DropdownMenuItem(
                                text = { Text("查看生成版 HTML") },
                                leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) },
                                onClick = { menuOpen = false; onViewHtml(false) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("导出独立 HTML") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                menuOpen = false
                                exporter.launch(safeExportName(plan.parsed.title))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除计划", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; deleteDialog = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (plan.parsed.capability == PlanCapability.ENHANCED && plan.parsed.days.isNotEmpty()) {
            NativePlanReader(
                plan = plan,
                modifier = Modifier.padding(padding),
                onAddPhotos = { pin ->
                    photoPin = pin
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemovePhoto = { photo ->
                    scope.launch {
                        photoError = repository.removePhoto(plan.id, photo.id).exceptionOrNull()?.message
                    }
                },
                onDeleteItems = repository::removeGalleryItems,
            )
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                PaperCard(Modifier.fillMaxWidth().padding(16.dp), background = Gold.copy(alpha = .35f)) {
                    Column {
                        Text("原页面阅读", style = MaterialTheme.typography.titleLarge)
                        Text("此文件未包含可识别的日期结构，按安全模式展示。", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onViewHtml(true) }) { Text("打开 HTML") }
                    }
                }
            }
        }
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("删除这份计划？") },
            text = { Text("计划、编辑版和本地缩略图会一并删除，原微信文件不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialog = false
                    scope.launch { repository.delete(plan.id); onDeleted() }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("取消") } },
        )
    }
    photoError?.let { message ->
        AlertDialog(
            onDismissRequest = { photoError = null },
            title = { Text("照片处理失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { photoError = null }) { Text("知道了") } },
        )
    }
}

@Composable
internal fun NativePlanReader(
    plan: StoredPlan,
    modifier: Modifier = Modifier,
    onAddPhotos: (PhotoPin) -> Unit = {},
    onRemovePhoto: (PlanPhoto) -> Unit = {},
    onDeleteItems: suspend (GalleryDeleteRequest) -> Result<GalleryDeleteResult> = {
        Result.success(GalleryDeleteResult(0, 0))
    },
) {
    val days = plan.parsed.days
    val pagerState = rememberPagerState(pageCount = { days.size })
    val dateListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activePage by remember { mutableStateOf(PlanReaderPage.ITINERARY) }
    var focusedItemId by remember { mutableStateOf<String?>(null) }
    var mapDragEnabled by remember { mutableStateOf(false) }
    var galleryPinId by remember { mutableStateOf<String?>(null) }
    var selectedDayIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastPagerPage by remember { mutableIntStateOf(pagerState.currentPage) }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPagerPage) focusedItemId = null
        if (pagerState.currentPage != lastPagerPage) mapDragEnabled = false
        lastPagerPage = pagerState.currentPage
        selectedDayIndex = pagerState.currentPage
    }

    LaunchedEffect(selectedDayIndex) {
        dateListState.animateScrollToItem(selectedDayIndex)
    }

    LaunchedEffect(activePage) {
        if (activePage != PlanReaderPage.MAP) mapDragEnabled = false
        if (
            activePage in setOf(PlanReaderPage.ITINERARY, PlanReaderPage.MAP) &&
            pagerState.currentPage != selectedDayIndex
        ) {
            pagerState.scrollToPage(selectedDayIndex)
        }
    }

    Column(modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Text(
                plan.parsed.destinations.joinToString(" · ") { it.name }.ifBlank { "旅行计划" },
                color = Coral,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(plan.parsed.title, style = MaterialTheme.typography.headlineLarge)
        }
        PlanReaderTabs(activePage) { page ->
            activePage = page
            if (page == PlanReaderPage.ALBUM) galleryPinId = null
            if (page != PlanReaderPage.MAP) {
                focusedItemId = null
                mapDragEnabled = false
            }
        }
        if (activePage == PlanReaderPage.ITINERARY || activePage == PlanReaderPage.MAP) {
            LazyRow(
                state = dateListState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().background(Paper),
            ) {
                itemsIndexed(days, key = { _, day -> day.id }) { index, day ->
                    val selected = index == selectedDayIndex
                    Column(
                        Modifier
                            .background(if (selected) Coral else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable {
                                focusedItemId = null
                                val action = PlanReaderDayPolicy.select(index, days.size, activePage)
                                selectedDayIndex = action.selectedIndex
                                action.pagerTarget?.let { target ->
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("DAY ${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                        Text(day.label.ifBlank { day.title }, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
        HorizontalDivider(color = Ink, thickness = 2.dp)
        when (activePage) {
            PlanReaderPage.ITINERARY -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                DayPage(
                    day = days[page],
                    sections = if (page == days.lastIndex) plan.parsed.sections else emptyList(),
                    onShowMap = { itemId ->
                        selectedDayIndex = page
                        focusedItemId = itemId
                        activePage = PlanReaderPage.MAP
                    },
                    onAddPhotos = { item -> onAddPhotos(PhotoPin(item.id, item.title)) },
                    onOpenPhotos = { item ->
                        galleryPinId = item.id
                        activePage = PlanReaderPage.ALBUM
                    },
                )
            }
            PlanReaderPage.MAP -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = shouldEnableDayPaging(mapDragEnabled),
            ) { page ->
                DailyMapPage(
                    plan = plan.parsed,
                    day = days[page],
                    focusedItemId = focusedItemId,
                    onDragEnabledChange = { enabled ->
                        if (page == pagerState.currentPage) mapDragEnabled = enabled
                    },
                    onAddPhotos = { stop -> onAddPhotos(PhotoPin(stop.itemId, stop.title)) },
                    onOpenPhotos = { stop ->
                        galleryPinId = stop.itemId
                        activePage = PlanReaderPage.ALBUM
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PlanReaderPage.BUDGET -> BudgetPage(plan.parsed, Modifier.fillMaxSize())
            PlanReaderPage.ALBUM -> PlanGalleryScreen(
                plan = plan,
                initialPinId = galleryPinId,
                onAddPhotos = onAddPhotos,
                onRemovePhoto = onRemovePhoto,
                onDeleteItems = onDeleteItems,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PlanReaderTabs(activePage: PlanReaderPage, onSelect: (PlanReaderPage) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PlanReaderPage.entries.forEach { page ->
            val selected = page == activePage
            Box(
                Modifier
                    .weight(1f)
                    .background(if (selected) Ink else Color.Transparent, RoundedCornerShape(999.dp))
                    .clickable { onSelect(page) }
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    page.label,
                    color = if (selected) Paper else Ink,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DayPage(
    day: PlanDayDraft,
    sections: List<PlanSectionDraft>,
    onShowMap: (String) -> Unit,
    onAddPhotos: (PlanItemDraft) -> Unit,
    onOpenPhotos: (PlanItemDraft) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 42.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(day.label, color = Coral, style = MaterialTheme.typography.labelLarge)
            Text(day.title, style = MaterialTheme.typography.headlineMedium)
        }
        items(day.items.size, key = { day.items[it].id }) { index ->
            ItineraryCard(day.items[index], index, onShowMap, onAddPhotos, onOpenPhotos)
        }
        if (day.items.isEmpty()) {
            item { Text("这一天还没有行程，进入编辑模式添加。", style = MaterialTheme.typography.bodyLarge) }
        }
        if (sections.isNotEmpty()) {
            item { Text("行前资料", color = Coral, style = MaterialTheme.typography.headlineMedium) }
            items(sections.size, key = { "section-$it-${sections[it].title}" }) { index ->
                val section = sections[index]
                PaperCard(Modifier.fillMaxWidth(), background = Gold.copy(alpha = .20f)) {
                    Column {
                        Text(section.title, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(section.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItineraryCard(
    item: PlanItemDraft,
    index: Int,
    onShowMap: (String) -> Unit,
    onAddPhotos: (PlanItemDraft) -> Unit,
    onOpenPhotos: (PlanItemDraft) -> Unit,
) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    PaperCard(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        background = when (index % 3) {
            0 -> Gold.copy(alpha = .30f)
            1 -> Mint.copy(alpha = .28f)
            else -> Paper
        },
    ) {
        Row {
            Column(Modifier.width(76.dp)) {
                Text(item.time.orEmpty().ifBlank { "—" }, color = Coral, style = MaterialTheme.typography.titleMedium)
                Text(PlanReaderPresentation.categoryLabel(item.category), style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleLarge)
                if (!item.notes.isNullOrBlank()) {
                    Text(
                        item.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!item.cost.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("费用 · ${item.cost}", style = MaterialTheme.typography.labelLarge)
                }
                if (expanded) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Ink.copy(alpha = .18f))
                    item.transport?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("下一程 · $it", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = { onShowMap(item.id) },
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("🗺️ 在每日地图中查看") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { onAddPhotos(item) },
                            contentPadding = PaddingValues(0.dp),
                        ) { Text("＋ 添加照片") }
                        TextButton(onClick = { onOpenPhotos(item) }) { Text("查看照片") }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("展开详情 ↓", color = Coral, style = MaterialTheme.typography.labelMedium)
                }
            }
            Icon(Icons.Filled.Train, contentDescription = null, tint = Ink.copy(alpha = .35f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun BudgetPage(plan: ParsedPlan, modifier: Modifier = Modifier) {
    val budgetSections = plan.sections.filter { section ->
        section.title.contains("预算") || section.title.contains("住宿") || section.title.contains("费用")
    }
    LazyColumn(
        modifier = modifier.background(Paper),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 42.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PaperCard(Modifier.fillMaxWidth(), background = Ink) {
                Column {
                    Text("行程总预算", color = Paper.copy(alpha = .68f), style = MaterialTheme.typography.labelLarge)
                    Text(plan.budget.orEmpty().ifBlank { "待估算" }, color = Gold, style = MaterialTheme.typography.headlineMedium)
                    val summary = listOfNotNull(plan.dateRange, plan.travelers, plan.baseArea).filter { it.isNotBlank() }
                    if (summary.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(summary.joinToString(" · "), color = Paper, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { Text("每日预算", color = Coral, style = MaterialTheme.typography.headlineMedium) }
        itemsIndexed(plan.days, key = { _, day -> "budget-${day.id}" }) { index, day ->
            PaperCard(Modifier.fillMaxWidth(), background = if (index % 2 == 0) Gold.copy(alpha = .22f) else Paper) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("DAY ${index + 1} · ${day.label}", style = MaterialTheme.typography.labelLarge)
                        Text(day.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(day.budget.orEmpty().ifBlank { "待估算" }, color = Coral, fontWeight = FontWeight.Black)
                }
            }
        }
        if (!plan.baseArea.isNullOrBlank() || !plan.accommodationBudget.isNullOrBlank()) {
            item {
                PaperCard(Modifier.fillMaxWidth(), background = Mint.copy(alpha = .24f)) {
                    Column {
                        Text("住宿", style = MaterialTheme.typography.titleLarge)
                        plan.baseArea?.takeIf { it.isNotBlank() }?.let { Text("落脚区域 · $it") }
                        plan.accommodationBudget?.takeIf { it.isNotBlank() }?.let { Text("住宿预算 · $it") }
                    }
                }
            }
        }
        itemsIndexed(budgetSections, key = { index, section -> "budget-section-$index-${section.title}" }) { _, section ->
            PaperCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(section.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(7.dp))
                    Text(section.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (plan.assumptions.isNotEmpty()) {
            item {
                PaperCard(Modifier.fillMaxWidth(), background = Gold.copy(alpha = .18f)) {
                    Column {
                        Text("预算说明", style = MaterialTheme.typography.titleLarge)
                        plan.assumptions.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

private fun safeExportName(title: String): String =
    title.replace(Regex("[\\/:*?\"<>|]"), "_").ifBlank { "旅行计划" } + ".html"
