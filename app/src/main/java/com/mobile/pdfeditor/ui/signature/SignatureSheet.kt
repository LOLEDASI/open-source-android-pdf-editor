package com.mobile.pdfeditor.ui.signature

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.pdfeditor.data.SavedSignature
import com.mobile.pdfeditor.data.SignatureKind
import com.mobile.pdfeditor.data.SignatureLibrary
import com.mobile.pdfeditor.signature.SignatureRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Method(val label: String) { Draw("Draw"), Type("Type"), Image("Image") }

private val inks = listOf(0x1A2B6B to "Navy", 0x15171A to "Black", 0x2457D6 to "Blue")

const val SIGNATURE_PAD_TAG = "signaturePad"

/**
 * Full-screen sheet for creating or picking a signature. [onPlace] receives a transparent,
 * cropped bitmap ready to put on the page.
 */
@Composable
fun SignatureSheet(
    library: SignatureLibrary,
    onPlace: (Bitmap, SignatureKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val saved by library.items.collectAsState()

    var kind by rememberSaveable { mutableStateOf(SignatureKind.Signature) }
    var method by rememberSaveable { mutableStateOf(Method.Draw) }
    var ink by rememberSaveable { mutableStateOf(inks[0].first) }
    var keep by rememberSaveable { mutableStateOf(true) }
    var typed by rememberSaveable { mutableStateOf("") }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var toDelete by remember { mutableStateOf<SavedSignature?>(null) }
    var working by remember { mutableStateOf(false) }

    val cleanedPhoto by produceState<Bitmap?>(null, photo, ink) {
        val src = photo
        value = if (src == null) null else withContext(Dispatchers.Default) { SignatureRender.cleanPhoto(src, ink) }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            working = true
            photo = withContext(Dispatchers.IO) { SignatureRender.decodePhoto(context, uri) }
            working = false
        }
    }

    val strokeWidthPx = with(density) { 2.5.dp.toPx() }
    val hasContent = when (method) {
        Method.Draw -> strokes.isNotEmpty()
        Method.Type -> typed.isNotBlank()
        Method.Image -> cleanedPhoto != null
    }

    fun place() {
        scope.launch {
            working = true
            val bmp = withContext(Dispatchers.Default) {
                when (method) {
                    Method.Draw -> SignatureRender.fromStrokes(strokes.map { s -> s.map { it.x to it.y } }, ink, strokeWidthPx)
                    Method.Type -> SignatureRender.fromText(typed.trim(), ink)
                    Method.Image -> cleanedPhoto
                }
            }
            if (bmp != null) {
                if (keep) library.add(bmp, kind)
                onPlace(bmp, kind)
            }
            working = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
                    Text(
                        if (kind == SignatureKind.Initials) "Add initials" else "Add signature",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        kind = if (kind == SignatureKind.Signature) SignatureKind.Initials else SignatureKind.Signature
                    }) { Text(if (kind == SignatureKind.Signature) "Initials" else "Signature") }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Method.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = m == method,
                                onClick = { method = m },
                                shape = SegmentedButtonDefaults.itemShape(i, Method.entries.size),
                            ) { Text(m.label) }
                        }
                    }

                    // The pad: draw here, or preview typed / photographed signatures.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val y = size.height - 44.dp.toPx()
                            drawLine(
                                Color(0xFFC3C8D0), Offset(24.dp.toPx(), y), Offset(size.width - 24.dp.toPx(), y),
                                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                            )
                        }
                        Text(
                            "Sign above the line",
                            fontSize = 12.sp,
                            color = Color(0xFF5B6270),
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 16.dp),
                        )
                        when (method) {
                            Method.Draw -> DrawPad(strokes, ink, strokeWidthPx)
                            Method.Type -> if (typed.isNotBlank()) {
                                Text(
                                    typed,
                                    fontFamily = FontFamily.Cursive,
                                    fontSize = 52.sp,
                                    color = Color(0xFF000000.toInt() or ink),
                                    maxLines = 1,
                                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                                )
                            }
                            Method.Image -> {
                                val c = cleanedPhoto
                                when {
                                    working -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                                    c != null -> Image(
                                        c.asImageBitmap(), contentDescription = "Signature from photo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                    )
                                    else -> Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                    ) {
                                        Icon(Icons.Outlined.Image, null, tint = Color(0xFF5B6270), modifier = Modifier.size(36.dp))
                                        Text(
                                            if (photo != null) "No ink found. Try a photo with darker ink on white paper."
                                            else "Photo of your signature on white paper",
                                            color = Color(0xFF5B6270), fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        }
                        if (hasContent) {
                            TextButton(
                                onClick = {
                                    when (method) {
                                        Method.Draw -> strokes.clear()
                                        Method.Type -> typed = ""
                                        Method.Image -> photo = null
                                    }
                                },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                            ) { Text("Clear") }
                        }
                    }

                    if (method == Method.Type) {
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it.take(40) },
                            label = { Text(if (kind == SignatureKind.Initials) "Your initials" else "Your name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (method == Method.Image) {
                        OutlinedButton(
                            onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (photo == null) "Choose a photo" else "Choose a different photo") }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ink", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.selectableGroup()) {
                            inks.forEach { (rgb, name) ->
                                val selected = rgb == ink
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .selectable(selected, role = Role.RadioButton, onClick = { ink = rgb })
                                        .semantics { contentDescription = name },
                                ) {
                                    if (selected) Box(Modifier.size(38.dp).border(2.dp, Color(0xFF000000.toInt() or rgb), CircleShape))
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or rgb)))
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .toggleable(keep, role = Role.Checkbox, onValueChange = { keep = it }),
                    ) {
                        Text("Save for later use", modifier = Modifier.weight(1f))
                        Checkbox(checked = keep, onCheckedChange = null)
                    }

                    if (saved.isNotEmpty()) {
                        Text("Saved", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(saved, key = { it.id }) { item ->
                                SavedTile(
                                    item = item,
                                    library = library,
                                    onPick = { bmp -> onPlace(bmp, item.kind) },
                                    onLongPress = { toDelete = item },
                                )
                            }
                        }
                        Text(
                            "Tap to place · long-press to delete",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                    Button(
                        onClick = ::place,
                        enabled = hasContent && !working,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    ) { Text("Place on page", fontSize = 16.sp) }
                }
            }
        }
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete saved ${if (item.kind == SignatureKind.Initials) "initials" else "signature"}?") },
            text = { Text("Signatures already placed in documents aren't affected.") },
            confirmButton = {
                TextButton(onClick = { library.delete(item); toDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DrawPad(strokes: MutableList<List<Offset>>, ink: Int, widthPx: Float) {
    val live = remember { mutableStateListOf<Offset>() }
    val color = Color(0xFF000000.toInt() or ink)
    Canvas(
        Modifier
            .fillMaxSize()
            .testTag(SIGNATURE_PAD_TAG)
            .semantics { contentDescription = "Signature pad" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    live.clear()
                    live.add(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        live.add(change.position)
                    }
                    strokes.add(live.toList())
                    live.clear()
                }
            },
    ) {
        val style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun draw(points: List<Offset>) {
            if (points.size == 1) {
                drawCircle(color, widthPx / 2, points[0])
                return
            }
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val p = points[i - 1]
                    val c = points[i]
                    quadraticTo(p.x, p.y, (p.x + c.x) / 2, (p.y + c.y) / 2)
                }
                lineTo(points.last().x, points.last().y)
            }
            drawPath(path, color, style = style)
        }
        strokes.forEach { if (it.isNotEmpty()) draw(it) }
        if (live.isNotEmpty()) draw(live)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedTile(item: SavedSignature, library: SignatureLibrary, onPick: (Bitmap) -> Unit, onLongPress: () -> Unit) {
    val bitmap by produceState<Bitmap?>(null, item.file) { value = library.load(item) }
    val label = if (item.kind == SignatureKind.Initials) "Saved initials" else "Saved signature"
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(if (item.kind == SignatureKind.Initials) 76.dp else 128.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClickLabel = "Place",
                onLongClickLabel = "Delete",
                onClick = { bitmap?.let(onPick) },
                onLongClick = onLongPress,
            )
            .semantics { contentDescription = label },
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
        }
    }
    Spacer(Modifier.width(0.dp))
}
