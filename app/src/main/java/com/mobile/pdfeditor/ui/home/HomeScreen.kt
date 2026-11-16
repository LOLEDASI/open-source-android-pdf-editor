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
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.mobile.pdfeditor.data.ImportNames
import com.mobile.pdfeditor.data.DevicePdf
import com.mobile.pdfeditor.data.DevicePdfs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    onOpenLink: (String) -> Unit,
    onTool: (com.mobile.pdfeditor.ui.tools.ToolId) -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val files by recents.files.collectAsState()
    var linkDialog by rememberSaveable { mutableStateOf(false) }
    if (linkDialog) {
        OpenLinkDialog(
            onOpen = { linkDialog = false; onOpenLink(it) },
            onDismiss = { linkDialog = false },
        )
    }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ListFilter.All) }
    val starredOnly = filter == ListFilter.Starred
    var sort by rememberSaveable { mutableStateOf(SortOrder.Recent) }

    // "On phone": every PDF on the device, once the user allows access to all files.
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(DevicePdfs.hasAccess(context)) }
    var devicePdfs by remember { mutableStateOf<List<DevicePdf>?>(null) }
    var scanToken by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        hasAccess = DevicePdfs.hasAccess(context)
        scanToken++ // files may have changed while we were away
        onPauseOrDispose { }
    }
    LaunchedEffect(filter, hasAccess, scanToken) {
        if (filter == ListFilter.Phone && hasAccess) {
            devicePdfs = try { DevicePdfs.scan(context) } catch (_: Exception) { emptyList() }
        }
    }
    val askLegacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasAccess = it }
    val requestAccess = {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                context.startActivity(DevicePdfs.settingsIntent(context))
            } catch (_: Exception) {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            askLegacy.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val visibleDevice = remember(devicePdfs, query, sort) {
        devicePdfs.orEmpty()
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.folder.contains(query.trim(), ignoreCase = true) }
            .let { list -> if (sort == SortOrder.Name) list.sortedBy { it.name.lowercase() } else list }
    }

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
                    IconButton(onClick = { linkDialog = true }) {
                        Icon(Icons.Outlined.Link, contentDescription = "Open PDF from a link")
                    }
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
                    onScan = { onTool(com.mobile.pdfeditor.ui.tools.ToolId.Scan) },
                    onImport = onOpenPicker,
                    onMerge = { onTool(com.mobile.pdfeditor.ui.tools.ToolId.Merge) },
                    onSign = { showMessage("Open a PDF first, then tap Sign") },
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                ) {
                    Text(if (filter == ListFilter.Phone) "On phone" else "Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilterChip(selected = filter == ListFilter.All, onClick = { filter = ListFilter.All }, label = { Text("All") })
                    FilterChip(selected = filter == ListFilter.Starred, onClick = { filter = ListFilter.Starred }, label = { Text("Starred") })
                    FilterChip(selected = filter == ListFilter.Phone, onClick = { filter = ListFilter.Phone }, label = { Text("On phone") })
                }
            }

            if (filter == ListFilter.Phone) {
                when {
                    !hasAccess -> item { AccessCard(onAllow = requestAccess) }
                    devicePdfs == null -> item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    visibleDevice.isEmpty() -> item {
                        Text(
                            if (devicePdfs.isNullOrEmpty()) "No PDFs found on this phone." else "No matching PDFs.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                        )
                    }
                    else -> {
                        item {
                            Text(
                                "${visibleDevice.size} PDF${if (visibleDevice.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            )
                        }
                        items(visibleDevice, key = { "d:" + it.uri }) { pdf -> DeviceRow(pdf, onOpen = { onOpenFile(pdf.uri) }) }
                    }
                }
            } else if (visible.isEmpty()) {
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

private enum class ListFilter { All, Starred, Phone }

@Composable
private fun AccessCard(onAllow: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("See every PDF on your phone", style = MaterialTheme.typography.titleSmall)
            Text(
                "Allow access to all files to list PDFs from Downloads, WhatsApp, Documents and everywhere else, " +
                    "and to save changes straight into them. Nothing leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAllow) { Text("Allow access") }
        }
    }
}

@Composable
private fun DeviceRow(pdf: DevicePdf, onOpen: () -> Unit) {
    val context = LocalContext.current
    val meta = remember(pdf) {
        listOf(
            Formatter.formatShortFileSize(context, pdf.sizeBytes),
            DateUtils.getRelativeTimeSpanString(pdf.modifiedAt).toString(),
            pdf.folder,
        ).filter { it.isNotBlank() }.joinToString(" · ")
    }
    Surface(onClick = onOpen, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Thumbnail(pdf.uri)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pdf.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun OpenLinkDialog(onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val url = ImportNames.pdfUrl(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open from link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste a link to a PDF, or a whole message with one in it. It will be downloaded and saved to Downloads/PDF Editor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("https://…") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { url?.let(onOpen) }, enabled = url != null) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
