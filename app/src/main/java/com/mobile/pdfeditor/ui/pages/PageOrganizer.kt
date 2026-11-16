package com.mobile.pdfeditor.ui.pages

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex
import com.mobile.pdfeditor.pages.PageEntry
import com.mobile.pdfeditor.pages.PagePlan
import kotlinx.coroutines.launch

/**
 * Edit the page list: tap to select, long-press and drag to reorder, then rotate, duplicate,
 * extract, delete or insert blank pages. Nothing touches the file until [onApply].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageOrganizer(
    pageSizes: List<Size>,
    saving: Boolean,
    renderThumb: suspend (pageIndex: Int, widthPx: Int) -> Bitmap?,
    onCancel: () -> Unit,
    onApply: (PagePlan) -> Unit,
    onExtract: (PagePlan) -> Unit,
) {
    var plan by remember { mutableStateOf(PagePlan.original(pageSizes.size)) }
    val history = remember { mutableStateListOf<PagePlan>() }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var dragKey by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun edit(next: PagePlan) {
        if (next != plan) {
            history.add(plan)
            plan = next
        }
    }
    fun say(text: String) = scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) }
    fun leave() = if (plan.isUnchanged) onCancel() else confirmDiscard = true

    BackHandler { leave() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.Outlined.Close, "Close page organizer") } },
                title = { Text(if (selected.isEmpty()) "Pages (${plan.size})" else "${selected.size} selected") },
                actions = {
                    IconButton(onClick = { history.removeLastOrNull()?.let { plan = it; selected = selected.filter { k -> it.pages.any { p -> p.key == k } }.toSet() } }, enabled = history.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, "Undo")
                    }
                    TextButton(onClick = {
                        selected = if (selected.size == plan.size) emptySet() else plan.pages.map { it.key }.toSet()
                    }) { Text(if (selected.size == plan.size) "Clear" else "Select all") }
                    Button(
                        onClick = { onApply(plan) },
                        enabled = !plan.isUnchanged && !saving,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Done") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val any = selected.isNotEmpty()
                Action("Rotate", Icons.Outlined.RotateRight, any) { edit(plan.rotate(selected)) }
                Action("Duplicate", Icons.Outlined.ContentCopy, any) { edit(plan.duplicate(selected)) }
                Action("Extract", Icons.Outlined.IosShare, any) { onExtract(plan.subset(selected)) }
                Action("Insert", Icons.Outlined.NoteAdd, true) {
                    val after = plan.pages.lastOrNull { it.key in selected } ?: plan.pages.last()
                    val size = after.let { if (it.isBlank) Size(it.blankWidth.toInt(), it.blankHeight.toInt()) else pageSizes[it.source] }
                    val (next, key) = plan.insertBlank(after.key, size.width.toFloat(), size.height.toFloat())
                    edit(next)
                    selected = setOf(key)
                }
                Action("Delete", Icons.Outlined.Delete, any, danger = true) {
                    if (selected.size >= plan.size) {
                        say("A PDF needs at least one page")
                    } else {
                        edit(plan.delete(selected))
                        selected = emptySet()
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            Column {
                Text(
                    "Tap to select · long-press and drag to move",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp),
                )
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("pageGrid")
                        .pointerInput(Unit) {
                            fun LazyGridItemInfo.rect() = Rect(offset.toOffset(), androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
                            detectDragGesturesAfterLongPress(
                                onDragStart = { pos ->
                                    gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.rect().contains(pos) }?.let {
                                        dragKey = it.key as? Long
                                        dragOffset = Offset.Zero
                                        if (dragKey != null) history.add(plan)
                                    }
                                },
                                onDragEnd = { dragKey = null; dragOffset = Offset.Zero },
                                onDragCancel = { dragKey = null; dragOffset = Offset.Zero },
                            ) { change, amount ->
                                val key = dragKey ?: return@detectDragGesturesAfterLongPress
                                change.consume()
                                dragOffset += amount
                                val items = gridState.layoutInfo.visibleItemsInfo
                                val current = items.firstOrNull { it.key == key } ?: return@detectDragGesturesAfterLongPress
                                val center = current.rect().center + dragOffset
                                val target = items.firstOrNull { it.key != key && it.rect().contains(center) } ?: return@detectDragGesturesAfterLongPress
                                val from = plan.pages.indexOfFirst { it.key == key }
                                val to = plan.pages.indexOfFirst { it.key == target.key }
                                plan = plan.move(from, to)
                                // The dragged item now lives at the target's slot; keep it under the finger.
                                dragOffset += (current.offset - target.offset).toOffset()
                            }
                        },
                ) {
                    itemsIndexed(plan.pages, key = { _, p -> p.key }) { index, entry ->
                        val isDragged = entry.key == dragKey
                        Thumb(
                            number = index + 1,
                            entry = entry,
                            pageSize = if (entry.isBlank) Size(entry.blankWidth.toInt(), entry.blankHeight.toInt()) else pageSizes[entry.source],
                            selected = entry.key in selected,
                            renderThumb = renderThumb,
                            onToggle = { selected = if (entry.key in selected) selected - entry.key else selected + entry.key },
                            modifier = if (isDragged) {
                                Modifier.zIndex(1f).graphicsLayer {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                }
                            } else {
                                Modifier.animateItem()
                            },
                        )
                    }
                }
            }
            if (saving) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard page changes?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onCancel() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Action(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = false,
        enabled = enabled,
        onClick = onClick,
        icon = { Icon(icon, null, tint = if (danger && enabled) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified) },
        label = { Text(label, maxLines = 1) },
    )
}

@Composable
private fun Thumb(
    number: Int,
    entry: PageEntry,
    pageSize: Size,
    selected: Boolean,
    renderThumb: suspend (Int, Int) -> Bitmap?,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    val widthPx = with(LocalDensity.current) { 120.dp.roundToPx() }
    val image by produceState<ImageBitmap?>(null, entry.source, entry.rotation) {
        if (entry.isBlank) return@produceState
        val bmp = renderThumb(entry.source, widthPx) ?: return@produceState
        value = if (entry.rotation == 0) bmp.asImageBitmap()
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(entry.rotation.toFloat()) }, true).asImageBitmap()
    }
    val sideways = entry.rotation % 180 != 0
    val aspect = if (sideways) pageSize.height.toFloat() / pageSize.width else pageSize.width.toFloat() / pageSize.height

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .toggleable(selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = "Page $number" },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.coerceIn(0.3f, 3f))
                .shadow(if (selected) 0.dp else 2.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else Modifier),
        ) {
            image?.let { Image(it, null, Modifier.fillMaxSize()) }
            if (entry.isBlank) Text("Blank", fontSize = 12.sp, color = Color(0xFF9AA1AD), modifier = Modifier.align(Alignment.Center))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.9f))
                    .then(if (selected) Modifier else Modifier.border(1.5.dp, Color(0xFF9AA1AD), CircleShape)),
            ) {
                if (selected) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Text(
            "$number",
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
