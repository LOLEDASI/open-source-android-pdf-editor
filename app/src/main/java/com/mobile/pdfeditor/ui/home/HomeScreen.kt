package com.mobile.pdfeditor.ui.home

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.pdfeditor.data.RecentFile
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.pdf.ThumbnailCache
import com.mobile.pdfeditor.ui.theme.Brand

private enum class SortOrder { Recent, Name }

@Composable
fun HomeScreen(
    recents: RecentFilesRepository,
    onOpenPicker: () -> Unit,
    onOpenFile: (String) -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val files by recents.files.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var starredOnly by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(SortOrder.Recent) }

    val visible = remember(files, query, starredOnly, sort) {
        files
            .filter { !starredOnly || it.starred }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .let { list -> if (sort == SortOrder.Name) list.sortedBy { it.name.lowercase() } else list }
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                ) {
                    Text("Documents", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        sort = if (sort == SortOrder.Recent) SortOrder.Name else SortOrder.Recent
                        showMessage(if (sort == SortOrder.Name) "Sorted by name" else "Sorted by last opened")
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Change sort order")
                    }
                }
            }
            item {
                SearchField(query, onChange = { query = it })
            }
            item {
                QuickActions(
                    onScan = { showMessage("Scanning is coming soon") },
                    onImport = onOpenPicker,
                    onMerge = { showMessage("Merge is coming soon") },
                    onSign = { showMessage("Open a PDF first, then tap Sign") },
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                ) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilterChip(selected = !starredOnly, onClick = { starredOnly = false }, label = { Text("All") })
                    FilterChip(selected = starredOnly, onClick = { starredOnly = true }, label = { Text("Starred") })
                }
            }

            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        hasAnyFiles = files.isNotEmpty(),
                        onOpenPicker = onOpenPicker,
                    )
                }
            } else {
                items(visible, key = { it.uri }) { file ->
                    FileRow(
                        file = file,
                        onOpen = { onOpenFile(file.uri) },
                        onToggleStar = { recents.toggleStar(file.uri) },
                        onRemove = { recents.remove(file.uri) },
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onOpenPicker,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Open PDF", fontWeight = FontWeight.SemiBold) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Search your PDFs") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun QuickActions(onScan: () -> Unit, onImport: () -> Unit, onMerge: () -> Unit, onSign: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        QuickAction("Scan", Icons.Outlined.DocumentScanner, onScan, Modifier.weight(1f))
        QuickAction("Import", Icons.Outlined.FileOpen, onImport, Modifier.weight(1f))
        QuickAction("Merge", Icons.Outlined.Layers, onMerge, Modifier.weight(1f))
        QuickAction("Sign", Icons.Outlined.Draw, onSign, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FileRow(file: RecentFile, onOpen: () -> Unit, onToggleStar: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val meta = remember(file) {
        buildList {
            if (file.pageCount > 0) add(if (file.pageCount == 1) "1 page" else "${file.pageCount} pages")
            if (file.sizeBytes > 0) add(Formatter.formatShortFileSize(context, file.sizeBytes))
            add(DateUtils.getRelativeTimeSpanString(file.openedAt).toString())
        }.joinToString(" · ")
    }

    Surface(onClick = onOpen, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Thumbnail(file.uri)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (file.starred) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Star, contentDescription = "Starred", tint = Color(0xFFE8A317), modifier = Modifier.size(16.dp))
                    }
                }
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More options for ${file.name}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (file.starred) "Remove star" else "Star") },
                        leadingIcon = { Icon(if (file.starred) Icons.Outlined.StarBorder else Icons.Filled.Star, null) },
                        onClick = { menuOpen = false; onToggleStar() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from recents") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(uri: String) {
    val context = LocalContext.current
    val widthPx = with(LocalDensity.current) { 44.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = ThumbnailCache.get(context, uri, widthPx)?.asImageBitmap()
    }
    Box(
        Modifier
            .width(44.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Outlined.PictureAsPdf,
                contentDescription = null,
                tint = Brand.PdfRed,
                modifier = Modifier.align(Alignment.Center).size(22.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(hasAnyFiles: Boolean, onOpenPicker: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(Icons.Outlined.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Text(
            if (hasAnyFiles) "No matching PDFs" else "No PDFs yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (hasAnyFiles) "Try a different search or filter."
            else "Open a PDF from your phone and it will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        if (!hasAnyFiles) {
            Button(onClick = onOpenPicker, modifier = Modifier.offset(y = 6.dp)) { Text("Open PDF") }
        }
    }
}
