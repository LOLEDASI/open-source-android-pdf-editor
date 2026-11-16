package com.mobile.pdfeditor.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mobile.pdfeditor.data.ImportException
import com.mobile.pdfeditor.data.IncomingPdf
import com.mobile.pdfeditor.data.PdfImporter
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.data.SignatureLibrary
import com.mobile.pdfeditor.ui.home.HomeScreen
import com.mobile.pdfeditor.ui.tools.OpenMode
import com.mobile.pdfeditor.ui.tools.ToolHost
import com.mobile.pdfeditor.ui.tools.ToolId
import com.mobile.pdfeditor.ui.tools.ToolsScreen
import com.mobile.pdfeditor.ui.viewer.ViewerScreen
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Files("Files", Icons.Outlined.Description, Icons.Filled.Description),
    Tools("Tools", Icons.Outlined.GridView, Icons.Filled.GridView),
}

@Composable
fun AppRoot(
    recents: RecentFilesRepository,
    signatures: SignatureLibrary,
    importer: PdfImporter,
    incoming: IncomingPdf?,
    incomingError: String?,
    onIncomingConsumed: () -> Unit,
) {
    var openDocument by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(Tab.Files) }
    var viewerDirty by remember { mutableStateOf(false) }
    /** An incoming PDF waiting for the user to confirm dropping unsaved changes. */
    var waiting by remember { mutableStateOf<IncomingPdf?>(null) }
    var importing by remember { mutableStateOf<IncomingPdf?>(null) }
    /** Shown once by the viewer after an import ("Saved to Downloads/…"). */
    var viewerNotice by remember { mutableStateOf<String?>(null) }
    var toolRequest by remember { mutableStateOf<ToolId?>(null) }
    /** Mode to open the next document in (from a tool), consumed by the viewer. */
    var openMode by remember { mutableStateOf(OpenMode.View) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    fun open(item: IncomingPdf) {
        scope.launch {
            importing = item
            try {
                val uri = importer.resolve(item)
                val copied = item !is IncomingPdf.View || !importer.isAlreadyOnDevice(item.uri)
                if (!copied) recents.persistAccess(uri)
                viewerDirty = false
                viewerNotice = if (copied) "Saved a copy to Downloads/${PdfImporter.FOLDER}" else null
                openDocument = uri.toString()
            } catch (e: ImportException) {
                (if (openDocument != null) { msg: String -> viewerNotice = msg } else showMessage)(e.message ?: "Couldn't open that PDF.")
            } catch (_: Exception) {
                showMessage("Couldn't open that PDF.")
            } finally {
                importing = null
            }
        }
    }

    LaunchedEffect(incoming, incomingError) {
        if (incomingError != null) showMessage(incomingError)
        if (incoming != null) {
            if (openDocument != null && viewerDirty) waiting = incoming else open(incoming)
        }
        if (incoming != null || incomingError != null) onIncomingConsumed()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            recents.persistAccess(uri)
            openDocument = uri.toString()
        }
    }
    val pickPdf = { picker.launch(arrayOf("application/pdf")) }

    val current = openDocument
    if (current != null) {
        // ViewerScreen handles Back itself (to leave annotate mode / confirm unsaved changes).
        ViewerScreen(
            uri = current,
            recents = recents,
            signatures = signatures,
            onBack = { openDocument = null; viewerDirty = false },
            onOpenDocument = { openDocument = it },
            onDirtyChange = { viewerDirty = it },
            startMode = openMode,
            onStartModeUsed = { openMode = OpenMode.View },
            notice = viewerNotice,
            onNoticeShown = { viewerNotice = null },
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { t ->
                        val selected = t == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { tab = t },
                            icon = { Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            when (tab) {
                Tab.Files -> HomeScreen(
                    recents = recents,
                    onOpenPicker = pickPdf,
                    onOpenFile = { openDocument = it },
                    onOpenLink = { open(IncomingPdf.Link(it)) },
                    onTool = { toolRequest = it },
                    showMessage = showMessage,
                    modifier = Modifier.padding(padding),
                )
                Tab.Tools -> ToolsScreen(
                    onTool = { toolRequest = it },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    ToolHost(
        request = toolRequest,
        onRequestHandled = { toolRequest = null },
        onOpen = { uri, mode ->
            if (mode != OpenMode.View) recents.persistAccess(uri)
            openMode = mode
            viewerDirty = false
            openDocument = uri.toString()
        },
        showMessage = { msg -> if (openDocument != null) viewerNotice = msg else showMessage(msg) },
    )

    importing?.let { item ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(if (item is IncomingPdf.Link) "Downloading PDF…" else "Opening PDF…")
                }
            },
        )
    }

    waiting?.let { item ->
        AlertDialog(
            onDismissRequest = { waiting = null },
            title = { Text("Open the new PDF?") },
            text = { Text("The document you're editing has unsaved changes. Opening another PDF will discard them.") },
            confirmButton = {
                TextButton(onClick = { waiting = null; open(item) }) {
                    Text("Discard and open", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { waiting = null }) { Text("Keep editing") } },
        )
    }
}
