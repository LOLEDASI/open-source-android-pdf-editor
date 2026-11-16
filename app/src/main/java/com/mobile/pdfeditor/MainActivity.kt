package com.mobile.pdfeditor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.mobile.pdfeditor.ui.AppRoot
import com.mobile.pdfeditor.ui.theme.PdfEditorTheme

class MainActivity : ComponentActivity() {
    /** A PDF handed to us by another app ("Open with"), consumed once by the UI. */
    private val incomingUri = mutableStateOf<String?>(null)

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
                    incomingUri = incomingUri.value,
                    onIncomingConsumed = { incomingUri.value = null },
                )
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { incomingUri.value = it.toString() }
        }
    }
}
