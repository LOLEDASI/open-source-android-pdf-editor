package com.mobile.pdfeditor.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.data.SignatureLibrary
import com.mobile.pdfeditor.ui.home.HomeScreen
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
    incomingUri: String?,
    onIncomingConsumed: () -> Unit,
) {
    var openDocument by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(Tab.Files) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            recents.persistAccess(Uri.parse(incomingUri))
            openDocument = incomingUri
            onIncomingConsumed()
        }
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
            onBack = { openDocument = null },
            onOpenDocument = { openDocument = it },
        )
        return
    }

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
                showMessage = showMessage,
                modifier = Modifier.padding(padding),
            )
            Tab.Tools -> ToolsScreen(
                showMessage = showMessage,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
