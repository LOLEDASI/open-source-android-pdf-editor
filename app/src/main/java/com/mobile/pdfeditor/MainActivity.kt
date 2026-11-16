package com.mobile.pdfeditor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.mobile.pdfeditor.data.ImportNames
import com.mobile.pdfeditor.data.IncomingPdf
import com.mobile.pdfeditor.ui.AppRoot
import com.mobile.pdfeditor.ui.theme.PdfEditorTheme

class MainActivity : ComponentActivity() {
    /** A PDF handed to us by another app, consumed once by the UI. */
    private val incoming = mutableStateOf<IncomingPdf?>(null)
    /** Shared content we couldn't use (e.g. text with no link), shown once. */
    private val incomingError = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        addOnNewIntentListener { handleIntent(it) }

        val app = application as PdfEditorApp
        setContent {
            PdfEditorTheme {
                AppRoot(
                    recents = app.recents,
                    signatures = app.signatures,
                    importer = app.importer,
                    incoming = incoming.value,
                    incomingError = incomingError.value,
                    onIncomingConsumed = {
                        incoming.value = null
                        incomingError.value = null
                    },
                )
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                incoming.value = if (data.scheme == "http" || data.scheme == "https") {
                    IncomingPdf.Link(data.toString())
                } else {
                    IncomingPdf.View(data)
                }
            }
            Intent.ACTION_SEND -> {
                val stream = intent.streamExtra()
                if (stream != null && intent.type?.startsWith("text/") != true) {
                    incoming.value = IncomingPdf.Shared(stream)
                    return
                }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val url = ImportNames.extractUrl(text)
                when {
                    url != null -> incoming.value = IncomingPdf.Link(url)
                    stream != null -> incoming.value = IncomingPdf.Shared(stream)
                    else -> incomingError.value = "There's no PDF or link in what was shared."
                }
            }
        }
    }

    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
}
