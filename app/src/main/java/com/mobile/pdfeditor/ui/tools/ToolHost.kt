package com.mobile.pdfeditor.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobile.pdfeditor.data.OutputStore
import com.mobile.pdfeditor.pages.PageRanges
import com.mobile.pdfeditor.pdf.PdfDocumentHandle
import com.mobile.pdfeditor.pdf.PdfTools
import com.mobile.pdfeditor.pdf.WrongPasswordException
import com.mobile.pdfeditor.signature.SignatureRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

enum class ToolId { Merge, Split, Compress, Reorder, Scan, ImagesToPdf, PdfToImages, Ocr, Password, Redact, Watermark, Flatten }

/** How a tool asks the app to open a file afterwards. */
enum class OpenMode { View, Pages, Redact }

private sealed interface Step {
    data class MergeOrder(val uris: List<Uri>) : Step
    data class Split(val file: File, val name: String, val pages: Int) : Step
    data class ImagesPageSize(val uris: List<Uri>) : Step
    data class ImageFormat(val uri: Uri, val name: String) : Step
    data class Lock(val file: File, val name: String) : Step
    data class Unlock(val file: File, val name: String, val error: String? = null) : Step
    data class Watermark(val file: File, val name: String) : Step
    data class ConfirmFlatten(val file: File, val name: String) : Step
}

/**
 * Runs the Tools tab's tools: picks the input, asks for any options, does the work off the main
 * thread with a progress dialog, saves the result to Downloads/PDF Editor and opens it.
 */
@Composable
fun ToolHost(
    request: ToolId?,
    onRequestHandled: () -> Unit,
    onOpen: (Uri, OpenMode) -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val output = remember { OutputStore(context) }
    var pending by remember { mutableStateOf<ToolId?>(null) }
    var step by remember { mutableStateOf<Step?>(null) }
    var working by remember { mutableStateOf<String?>(null) }

    fun run(label: String, block: suspend () -> Unit) {
        scope.launch {
            working = label
            try {
                block()
            } catch (e: WrongPasswordException) {
                showMessage(e.message ?: "Wrong password")
            } catch (e: Exception) {
                showMessage(e.message?.takeIf { it.length < 120 && e is com.mobile.pdfeditor.data.ImportException } ?: "That didn't work on this file.")
            } finally {
                working = null
            }
        }
    }

    fun nameOf(uri: Uri) = PdfDocumentHandle.readMeta(context, uri).name.removeSuffix(".pdf").removeSuffix(".PDF")

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val tool = pending
        pending = null
        if (uri == null || tool == null) return@rememberLauncherForActivityResult
        when (tool) {
            ToolId.Reorder -> onOpen(uri, OpenMode.Pages)
            ToolId.Redact -> onOpen(uri, OpenMode.Redact)
            ToolId.PdfToImages -> step = Step.ImageFormat(uri, nameOf(uri))
            else -> run("Reading PDF…") {
                val name = nameOf(uri)
                val file = withContext(Dispatchers.IO) { PdfTools.copyToCache(context, uri) }
                when (tool) {
                    ToolId.Password -> step = if (withContext(Dispatchers.IO) { PdfTools.needsPassword(file) }) Step.Unlock(file, name) else Step.Lock(file, name)
                    ToolId.Split -> step = Step.Split(file, name, withContext(Dispatchers.IO) { PdfTools.pageCount(file) })
                    ToolId.Watermark -> step = Step.Watermark(file, name)
                    ToolId.Flatten -> step = Step.ConfirmFlatten(file, name)
                    ToolId.Compress -> {
                        working = "Compressing…"
                        val out = File.createTempFile("out-", ".pdf", context.cacheDir)
                        val smaller = withContext(Dispatchers.IO) { PdfTools.compress(file, out) }
                        if (!smaller) {
                            showMessage("This PDF is already compact. Nothing to shrink.")
                        } else {
                            val result = withContext(Dispatchers.IO) { output.savePdf(out, "$name (compressed).pdf") }
                            showMessage("${Formatter.formatShortFileSize(context, file.length())} → ${Formatter.formatShortFileSize(context, out.length())}")
                            onOpen(result, OpenMode.View)
                        }
                        out.delete()
                    }
                    else -> Unit
                }
            }
        }
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pending = null
        when {
            uris.size >= 2 -> step = Step.MergeOrder(uris)
            uris.size == 1 -> showMessage("Pick at least two PDFs to merge")
        }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        pending = null
        if (uris.isNotEmpty()) step = Step.ImagesPageSize(uris)
    }

    LaunchedEffect(request) {
        val tool = request ?: return@LaunchedEffect
        onRequestHandled()
        pending = tool
        when (tool) {
            ToolId.Merge -> pickMany.launch(arrayOf("application/pdf"))
            ToolId.ImagesToPdf -> pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            ToolId.Scan, ToolId.Ocr -> { pending = null; showMessage("Coming in the next update") }
            else -> pickOne.launch(arrayOf("application/pdf"))
        }
    }

    when (val s = step) {
        null -> Unit
        is Step.MergeOrder -> MergeDialog(context, s.uris, onDismiss = { step = null }) { ordered ->
            step = null
            run("Merging…") {
                val files = withContext(Dispatchers.IO) { ordered.map { PdfTools.copyToCache(context, it) } }
                val out = File.createTempFile("out-", ".pdf", context.cacheDir)
                withContext(Dispatchers.IO) { PdfTools.merge(files, out) }
                val result = withContext(Dispatchers.IO) { output.savePdf(out, "${nameOf(ordered.first())} (merged).pdf") }
                (files + out).forEach { it.delete() }
                onOpen(result, OpenMode.View)
            }
        }
        is Step.Split -> SplitDialog(s.pages, onDismiss = { step = null }) { groups ->
            step = null
            run("Splitting…") {
                val outs = withContext(Dispatchers.IO) { PdfTools.split(s.file, groups, context.cacheDir) }
                val saved = withContext(Dispatchers.IO) {
                    outs.mapIndexed { i, f -> output.savePdf(f, "${s.name} (part ${i + 1}).pdf").also { f.delete() } }
                }
                showMessage("Saved ${saved.size} files to Downloads/${OutputStore.FOLDER}")
                saved.firstOrNull()?.let { onOpen(it, OpenMode.View) }
            }
        }
        is Step.ImagesPageSize -> ChoiceDialog(
            title = "Page size",
            options = listOf("A4 pages (with margins)", "Match each image"),
            confirm = "Create PDF",
            onDismiss = { step = null },
        ) { choice ->
            step = null
            run("Creating PDF…") {
                val bitmaps = withContext(Dispatchers.IO) { s.uris.mapNotNull { SignatureRender.decodePhoto(context, it, maxSide = 2400) } }
                if (bitmaps.isEmpty()) throw com.mobile.pdfeditor.data.ImportException("Couldn't read those images.")
                val out = File.createTempFile("out-", ".pdf", context.cacheDir)
                withContext(Dispatchers.IO) { PdfTools.imagesToPdf(bitmaps, out, a4 = choice == 0) }
                val result = withContext(Dispatchers.IO) { output.savePdf(out, "Images ${System.currentTimeMillis() / 1000}.pdf") }
                out.delete()
                onOpen(result, OpenMode.View)
            }
        }
        is Step.ImageFormat -> ChoiceDialog(
            title = "Save pages as",
            options = listOf("PNG (sharp, larger files)", "JPG (smaller files)"),
            confirm = "Save images",
            onDismiss = { step = null },
        ) { choice ->
            step = null
            run("Saving images…") {
                val count = withContext(Dispatchers.IO) { exportImages(context, output, s.uri, s.name, png = choice == 0) }
                showMessage("Saved $count images to Pictures/${OutputStore.FOLDER}")
            }
        }
        is Step.Lock -> PasswordDialog(
            title = "Protect with a password",
            text = "Anyone opening the copy will need this password. Keep it safe: it can't be recovered.",
            confirmTwice = true,
            error = null,
            onDismiss = { step = null },
        ) { pw ->
            step = null
            run("Encrypting…") {
                val out = File.createTempFile("out-", ".pdf", context.cacheDir)
                withContext(Dispatchers.IO) { PdfTools.lock(s.file, out, pw) }
                withContext(Dispatchers.IO) { output.savePdf(out, "${s.name} (protected).pdf") }
                out.delete()
                showMessage("Saved a password-protected copy to Downloads/${OutputStore.FOLDER}")
            }
        }
        is Step.Unlock -> PasswordDialog(
            title = "Remove password",
            text = "This PDF is password-protected. Enter its password to save an unlocked copy.",
            confirmTwice = false,
            error = s.error,
            onDismiss = { step = null },
        ) { pw ->
            run("Unlocking…") {
                val out = File.createTempFile("out-", ".pdf", context.cacheDir)
                try {
                    withContext(Dispatchers.IO) { PdfTools.unlock(s.file, out, pw) }
                } catch (e: WrongPasswordException) {
                    step = s.copy(error = e.message)
                    return@run
                }
                step = null
                val result = withContext(Dispatchers.IO) { output.savePdf(out, "${s.name} (unlocked).pdf") }
                out.delete()
                onOpen(result, OpenMode.View)
            }
        }
        is Step.Watermark -> WatermarkDialog(onDismiss = { step = null }) { text, opacity ->
            step = null
            run("Adding watermark…") {
                val out = File.createTempFile("out-", ".pdf", context.cacheDir)
                withContext(Dispatchers.IO) { PdfTools.watermark(s.file, out, text, opacity) }
                val result = withContext(Dispatchers.IO) { output.savePdf(out, "${s.name} (watermarked).pdf") }
                out.delete()
                onOpen(result, OpenMode.View)
            }
        }
        is Step.ConfirmFlatten -> AlertDialog(
            onDismissRequest = { step = null },
            title = { Text("Flatten PDF?") },
            text = { Text("Annotations, signatures and form fields become a fixed part of the pages in a new copy, so they can't be moved or changed. The original stays as it is.") },
            confirmButton = {
                TextButton(onClick = {
                    step = null
                    run("Flattening…") {
                        val out = File.createTempFile("out-", ".pdf", context.cacheDir)
                        withContext(Dispatchers.IO) { PdfTools.flatten(s.file, out) }
                        val result = withContext(Dispatchers.IO) { output.savePdf(out, "${s.name} (flattened).pdf") }
                        out.delete()
                        onOpen(result, OpenMode.View)
                    }
                }) { Text("Flatten") }
            },
            dismissButton = { TextButton(onClick = { step = null }) { Text("Cancel") } },
        )
    }

    working?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(label)
                }
            },
        )
    }
}

/** Renders each page at 150 dpi and saves it to Pictures/PDF Editor. Returns how many were saved. */
private suspend fun exportImages(context: Context, output: OutputStore, uri: Uri, name: String, png: Boolean): Int {
    val doc = PdfDocumentHandle.open(context, uri)
    try {
        var n = 0
        doc.pageSizes.forEachIndexed { i, size ->
            val bmp: Bitmap = doc.render(i, (size.width * 150f / 72f).roundToInt()) ?: return@forEachIndexed
            output.saveImage(bmp, "$name - page ${i + 1}.${if (png) "png" else "jpg"}", png)
            bmp.recycle()
            n++
        }
        return n
    } finally {
        doc.close()
    }
}

@Composable
private fun MergeDialog(context: Context, initial: List<Uri>, onDismiss: () -> Unit, onMerge: (List<Uri>) -> Unit) {
    var order by remember { mutableStateOf(initial) }
    val names = remember(initial) { initial.associateWith { PdfDocumentHandle.readMeta(context, it).name } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge ${order.size} PDFs") },
        text = {
            Column {
                Text("In this order:", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(order, key = { _, u -> u.toString() }) { i, uri ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. ${names[uri]}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { order = order.toMutableList().apply { add(i - 1, removeAt(i)) } }, enabled = i > 0) {
                                Icon(Icons.Outlined.KeyboardArrowUp, "Move up")
                            }
                            IconButton(onClick = { order = order.toMutableList().apply { add(i + 1, removeAt(i)) } }, enabled = i < order.lastIndex) {
                                Icon(Icons.Outlined.KeyboardArrowDown, "Move down")
                            }
                            IconButton(onClick = { order = order - uri }, enabled = order.size > 2) {
                                Icon(Icons.Outlined.Close, "Remove")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMerge(order) }) { Text("Merge") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SplitDialog(pageCount: Int, onDismiss: () -> Unit, onSplit: (List<List<Int>>) -> Unit) {
    var mode by remember { mutableStateOf(0) }
    var everyN by remember { mutableStateOf("2") }
    var ranges by remember { mutableStateOf("") }
    val result: Result<List<List<Int>>> = when (mode) {
        0 -> Result.success(PageRanges.chunks(pageCount, 1))
        1 -> everyN.toIntOrNull()?.takeIf { it in 1..pageCount }?.let { Result.success(PageRanges.chunks(pageCount, it)) }
            ?: Result.failure(IllegalArgumentException("Enter a number from 1 to $pageCount"))
        else -> PageRanges.parse(ranges, pageCount)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split $pageCount pages") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Every page as its own file", "Every N pages", "Custom ranges").forEachIndexed { i, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().selectable(mode == i, role = Role.RadioButton) { mode = i },
                    ) {
                        RadioButton(selected = mode == i, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (mode == 1) {
                    OutlinedTextField(everyN, { everyN = it.filter(Char::isDigit) }, label = { Text("Pages per file") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (mode == 2) {
                    OutlinedTextField(ranges, { ranges = it }, label = { Text("e.g. 1-3, 5, 8-") }, singleLine = true)
                }
                result.exceptionOrNull()?.let { if (mode != 2 || ranges.isNotBlank()) Text(it.message ?: "", color = MaterialTheme.colorScheme.error) }
                result.getOrNull()?.let { Text("Creates ${it.size} file${if (it.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { result.getOrNull()?.let(onSplit) }, enabled = result.isSuccess) { Text("Split") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, confirm: String, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var choice by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().selectable(choice == i, role = Role.RadioButton) { choice = i }.padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = choice == i, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choice) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordDialog(title: String, text: String, confirmTwice: Boolean, error: String?, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val mismatch = confirmTwice && again.isNotEmpty() && pw != again
    val ok = pw.isNotEmpty() && (!confirmTwice || pw == again)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = error != null)
                if (confirmTwice) {
                    OutlinedTextField(again, { again = it }, label = { Text("Type it again") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = mismatch)
                }
                (error ?: if (mismatch) "The passwords don't match" else null)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(pw) }, enabled = ok) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WatermarkDialog(onDismiss: () -> Unit, onApply: (String, Float) -> Unit) {
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var opacity by remember { mutableStateOf(0.25f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add watermark") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it.take(40) }, label = { Text("Text") }, singleLine = true)
                Text("Strength ${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..0.6f)
            }
        },
        confirmButton = { TextButton(onClick = { onApply(text.trim(), opacity) }, enabled = text.isNotBlank()) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
