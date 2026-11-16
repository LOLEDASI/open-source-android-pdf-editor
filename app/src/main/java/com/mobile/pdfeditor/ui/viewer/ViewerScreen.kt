package com.mobile.pdfeditor.ui.viewer

import android.content.Intent
import android.net.Uri
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.pdfeditor.annot.InkDefaults
import com.mobile.pdfeditor.annot.InkStroke
import com.mobile.pdfeditor.annot.InkStyle
import com.mobile.pdfeditor.annot.InkTool
import com.mobile.pdfeditor.annot.PagePoint
import com.mobile.pdfeditor.annot.PageRect
import com.mobile.pdfeditor.annot.SignatureStamp
import com.mobile.pdfeditor.annot.StampGeometry
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.data.SignatureKind
import com.mobile.pdfeditor.data.SignatureLibrary
import com.mobile.pdfeditor.ui.signature.SignatureSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val PAGE_VIEWPORT_TAG = "pageViewport"
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** What the page list needs to draw and edit ink. */
private class InkUi(
    val strokesByPage: Map<Int, List<InkStroke>>,
    val tool: EditTool?,
    val penStyle: InkStyle,
    val highlighterStyle: InkStyle,
    val onStroke: (page: Int, InkTool, InkStyle, List<PagePoint>) -> Unit,
    val onErase: (Set<Long>) -> Unit,
)

/** What the page list needs to show and edit placed signatures. */
private class StampUi(
    val stampsByPage: Map<Int, List<SignatureStamp>>,
    val images: Map<String, android.graphics.Bitmap>,
    val selectedId: Long?,
    val onSelect: (Long) -> Unit,
    val onDeselect: () -> Unit,
    val onMove: (Long, PageRect) -> Unit,
    val onDelete: (Long) -> Unit,
)

/** The page filling most of the viewport, and the page-point y at the viewport's centre on it. */
private fun visiblePageCenter(listState: LazyListState, sizes: List<Size>): Pair<Int, Float> {
    val info = listState.layoutInfo
    val viewportMid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val best = info.visibleItemsInfo.maxByOrNull { item ->
        minOf(item.offset + item.size, info.viewportEndOffset) - maxOf(item.offset, info.viewportStartOffset)
    } ?: return 0 to sizes[0].height / 2f
    val page = best.index.coerceIn(0, sizes.lastIndex)
    val fraction = ((viewportMid - best.offset) / best.size).coerceIn(0.15f, 0.85f)
    return page to fraction * sizes[page].height
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    uri: String,
    recents: RecentFilesRepository,
    signatures: SignatureLibrary,
    onBack: () -> Unit,
    /** Switch the viewer to another file, e.g. a copy the user just saved. */
    onOpenDocument: (String) -> Unit = {},
    /** Told whenever unsaved changes appear or go away. */
    onDirtyChange: (Boolean) -> Unit = {},
    /** A one-off message to show when the viewer opens (e.g. where an import was saved). */
    notice: String? = null,
    onNoticeShown: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(uri) { ViewerController(context, Uri.parse(uri), recents, scope) }
    DisposableEffect(controller) { onDispose { controller.close() } }

    val state by controller.state.collectAsState()
    val ink by controller.annotations.state.collectAsState()
    val inkStatus by controller.inkStatus.collectAsState()
    val saving by controller.saving.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val ready = state as? ViewerUiState.Ready

    var annotating by rememberSaveable { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf(EditTool.Pen) }
    var penStyle by remember { mutableStateOf(InkDefaults.Pen) }
    var highlighterStyle by remember { mutableStateOf(InkDefaults.Highlighter) }
    var settingsFor by remember { mutableStateOf<InkTool?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var exitAfterSave by remember { mutableStateOf(false) }
    var showSignatureSheet by remember { mutableStateOf(false) }
    var selectedStamp by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(annotating) { if (annotating) selectedStamp = null }

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    LaunchedEffect(ink.dirty) { onDirtyChange(ink.dirty) }
    LaunchedEffect(notice) {
        if (notice != null) {
            message(notice)
            onNoticeShown()
        }
    }

    val copyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        if (target == null) {
            exitAfterSave = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            when (val r = controller.saveCopy(target)) {
                SaveResult.Saved -> if (exitAfterSave) onBack() else onOpenDocument(target.toString())
                is SaveResult.Failed -> message(r.message)
                SaveResult.NeedsCopy -> Unit
            }
            exitAfterSave = false
        }
    }

    fun save(thenExit: Boolean) {
        scope.launch {
            when (val r = controller.save()) {
                SaveResult.Saved -> if (thenExit) onBack() else message("Saved")
                SaveResult.NeedsCopy -> {
                    exitAfterSave = thenExit
                    message("This file can't be changed in place. Choose where to save a copy.")
                    val base = ready?.name?.removeSuffix(".pdf")?.removeSuffix(".PDF") ?: "Document"
                    copyLauncher.launch("$base (annotated).pdf")
                }
                is SaveResult.Failed -> message(r.message)
            }
        }
    }

    fun leave() {
        if (ink.dirty) confirmExit = true else onBack()
    }

    BackHandler {
        when {
            annotating -> annotating = false
            else -> leave()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to documents")
                    }
                },
                title = {
                    Column {
                        Text(
                            ready?.name ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (ready != null) {
                            val n = ready.pageSizes.size
                            Text(
                                when {
                                    saving -> "Saving…"
                                    ink.dirty -> "Unsaved changes"
                                    n == 1 -> "1 page"
                                    else -> "$n pages"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (ready != null) {
                        if (annotating || ink.canUndo || ink.canRedo) {
                            IconButton(onClick = controller.annotations::undo, enabled = ink.canUndo) {
                                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
                            }
                            IconButton(onClick = controller.annotations::redo, enabled = ink.canRedo) {
                                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo")
                            }
                        }
                        if (ink.dirty || saving) {
                            Button(
                                onClick = { save(thenExit = false) },
                                enabled = !saving,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.padding(end = 8.dp),
                            ) { Text("Save") }
                        } else {
                            IconButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("application/pdf")
                                    .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, "Share PDF"))
                            }) {
                                Icon(Icons.Outlined.Share, contentDescription = "Share")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            if (ready != null) {
                EditModeBar(annotating = annotating, onPick = { label ->
                    if (label == ANNOTATE) {
                        when (val s = inkStatus) {
                            InkStatus.Ready -> annotating = !annotating
                            InkStatus.Loading -> message("Getting annotations ready…")
                            is InkStatus.Unavailable -> message(s.reason)
                        }
                    } else if (label == SIGN) {
                        when (val s = inkStatus) {
                            InkStatus.Ready -> { annotating = false; showSignatureSheet = true }
                            InkStatus.Loading -> message("Getting the document ready…")
                            is InkStatus.Unavailable -> message(s.reason.replace("annotated", "signed"))
                        }
                    } else {
                        message("$label is coming in the next build")
                    }
                })
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            when (val s = state) {
                ViewerUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ViewerUiState.Error -> ErrorState(s.message, onBack, Modifier.align(Alignment.Center))
                is ViewerUiState.Ready -> {
                    val strokesByPage = remember(ink.strokes) { ink.strokes.groupBy { it.page } }
                    val stampsByPage = remember(ink.stamps) { ink.stamps.groupBy { it.page } }
                    PageList(
                        pageSizes = s.pageSizes,
                        controller = controller,
                        listState = listState,
                        stamps = StampUi(
                            stampsByPage = stampsByPage,
                            images = controller.stampImages,
                            selectedId = selectedStamp,
                            onSelect = { selectedStamp = it },
                            onDeselect = { selectedStamp = null },
                            onMove = { id, rect -> controller.annotations.moveStamp(id, rect) },
                            onDelete = { id -> controller.annotations.remove(setOf(id)); selectedStamp = null },
                        ),
                        ink = InkUi(
                            strokesByPage = strokesByPage,
                            tool = if (annotating) tool else null,
                            penStyle = penStyle,
                            highlighterStyle = highlighterStyle,
                            onStroke = { page, t, style, pts ->
                                val store = controller.annotations
                                store.add(InkStroke(store.newId(), page, t, style, pts))
                            },
                            onErase = { controller.annotations.remove(it) },
                        ),
                        indicatorBottomPadding = if (annotating) 88.dp else 16.dp,
                    )
                    if (annotating) {
                        InkToolbar(
                            tool = tool,
                            onTool = { tool = it },
                            swatch = if (tool == EditTool.Highlighter) highlighterStyle else penStyle,
                            onOpenSettings = {
                                settingsFor = if (tool == EditTool.Highlighter) InkTool.Highlighter else InkTool.Pen
                            },
                            onClose = { annotating = false },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        )
                    }
                }
            }
            if (saving) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    if (showSignatureSheet) {
        SignatureSheet(
            library = signatures,
            onPlace = { bitmap, kind ->
                showSignatureSheet = false
                val sizes = ready?.pageSizes ?: return@SignatureSheet
                val (page, centerY) = visiblePageCenter(listState, sizes)
                val size = sizes[page]
                selectedStamp = controller.placeSignature(
                    bitmap = bitmap,
                    page = page,
                    pageW = size.width.toFloat(),
                    pageH = size.height.toFloat(),
                    widthPt = if (kind == SignatureKind.Initials) StampGeometry.INITIALS_WIDTH_PT else StampGeometry.DEFAULT_WIDTH_PT,
                    centerY = centerY,
                )
                message("Drag to move · corner to resize")
            },
            onDismiss = { showSignatureSheet = false },
        )
    }

    settingsFor?.let { kind ->
        InkSettingsSheet(
            kind = kind,
            onKind = {
                settingsFor = it
                tool = if (it == InkTool.Highlighter) EditTool.Highlighter else EditTool.Pen
            },
            style = if (kind == InkTool.Highlighter) highlighterStyle else penStyle,
            onStyle = { if (kind == InkTool.Highlighter) highlighterStyle = it else penStyle = it },
            onDismiss = { settingsFor = null },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Save changes?") },
            text = { Text("Your annotations on ${ready?.name ?: "this PDF"} haven't been saved.") },
            confirmButton = {
                Button(onClick = { confirmExit = false; save(thenExit = true) }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false }) { Text("Cancel") }
                    TextButton(onClick = { confirmExit = false; onBack() }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }
}

@Composable
private fun PageList(
    pageSizes: List<Size>,
    controller: ViewerController,
    listState: LazyListState,
    stamps: StampUi,
    ink: InkUi,
    indicatorBottomPadding: androidx.compose.ui.unit.Dp,
) {
    val deselectState = rememberUpdatedState(stamps.onDeselect)
    fun deselect() = deselectState.value()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val drawing by rememberUpdatedState(ink.tool != null)

    // Re-render sharper bitmaps once a zoom gesture settles.
    var quality by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(scale) {
        delay(250)
        quality = when {
            scale >= 2.5f -> 2.5f
            scale >= 1.5f -> 1.75f
            else -> 1f
        }
    }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = viewport.width * (s - 1f) / 2f
        val maxY = viewport.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .testTag(PAGE_VIEWPORT_TAG)
            .semantics { stateDescription = "Zoom ${(scale * 100).roundToInt()}%" }
            // Pinch to zoom. Runs in the Initial pass so two-finger gestures don't also scroll the list
            // or draw. While annotating, one finger draws and two fingers zoom and scroll.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            val pan = event.calculatePan()
                            if (drawing) {
                                offset = clamp(Offset(offset.x + pan.x, offset.y), newScale)
                                listState.dispatchRawDelta(-pan.y / newScale)
                            } else {
                                offset = clamp(offset + pan, newScale)
                            }
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (scale > 1f && !drawing) {
                            offset = clamp(Offset(offset.x + event.calculatePan().x, offset.y), scale)
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(ink.tool == null) {
                if (ink.tool != null) return@pointerInput // double-tap would leave dots while drawing
                detectTapGestures(onTap = { deselect() }, onDoubleTap = { tap ->
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        val center = Offset(viewport.width / 2f, viewport.height / 2f)
                        scale = DOUBLE_TAP_ZOOM
                        // Keep the tapped point under the finger.
                        offset = clamp((tap - center) * (1f - DOUBLE_TAP_ZOOM), DOUBLE_TAP_ZOOM)
                    }
                })
            },
    ) {
        val sidePadding = 12.dp
        val baseWidthPx = with(LocalDensity.current) { (maxWidth - sidePadding * 2).roundToPx() }
        val renderWidth = (baseWidthPx * quality).toInt().coerceAtLeast(1)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            itemsIndexed(pageSizes) { index, size ->
                PageItem(index, size, renderWidth, controller, ink, stamps)
            }
        }

        PageIndicator(
            current = listState.firstVisibleItemIndex + 1,
            total = pageSizes.size,
            visible = listState.isScrollInProgress,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = indicatorBottomPadding),
        )
    }
}

@Composable
private fun PageItem(index: Int, size: Size, widthPx: Int, controller: ViewerController, ink: InkUi, stamps: StampUi) {
    var bitmap by remember(index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(index, widthPx) {
        controller.cached(index, widthPx)?.let { bitmap = it.asImageBitmap(); return@LaunchedEffect }
        controller.render(index, widthPx)?.let { bitmap = it.asImageBitmap() }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(size.width.toFloat() / size.height.toFloat())
            .shadow(2.dp)
            .background(Color.White),
    ) {
        bitmap?.let {
            Image(it, contentDescription = "Page ${index + 1}", modifier = Modifier.fillMaxSize())
        }
        InkLayer(
            pageWidthPt = size.width.toFloat(),
            strokes = ink.strokesByPage[index].orEmpty(),
            tool = ink.tool,
            penStyle = ink.penStyle,
            highlighterStyle = ink.highlighterStyle,
            onStroke = { t, style, pts -> ink.onStroke(index, t, style, pts) },
            onErase = ink.onErase,
            modifier = Modifier.testTag("ink-$index"),
        )
        StampLayer(
            pageWidthPt = size.width.toFloat(),
            pageHeightPt = size.height.toFloat(),
            stamps = stamps.stampsByPage[index].orEmpty(),
            images = stamps.images,
            selectedId = stamps.selectedId,
            onSelect = stamps.onSelect,
            onMove = stamps.onMove,
            onDelete = stamps.onDelete,
        )
    }
}

@Composable
private fun PageIndicator(current: Int, total: Int, visible: Boolean, modifier: Modifier) {
    // Stay up briefly after scrolling stops.
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) show = true else { delay(1200); show = false }
    }
    AnimatedVisibility(show, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(color = Color(0xD115171A), contentColor = Color.White, shape = RoundedCornerShape(14.dp)) {
            Text(
                "Page $current of $total",
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private const val ANNOTATE = "Annotate"
private const val SIGN = "Sign"

private data class EditMode(val label: String, val icon: ImageVector)

private val editModes = listOf(
    EditMode(ANNOTATE, Icons.Outlined.Draw),
    EditMode("Edit text", Icons.Outlined.TextFields),
    EditMode("Fill form", Icons.AutoMirrored.Outlined.Assignment),
    EditMode(SIGN, Icons.Outlined.Gesture),
    EditMode("Pages", Icons.Outlined.GridView),
)

@Composable
private fun EditModeBar(annotating: Boolean, onPick: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        editModes.forEach { mode ->
            NavigationBarItem(
                selected = mode.label == ANNOTATE && annotating,
                onClick = { onPick(mode.label) },
                icon = { Icon(mode.icon, contentDescription = null) },
                label = { Text(mode.label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(32.dp),
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onBack) { Text("Back to documents") }
    }
}
