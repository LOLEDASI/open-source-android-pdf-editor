package com.mobile.pdfeditor

import android.app.Application
import com.mobile.pdfeditor.data.RecentFilesRepository
import com.mobile.pdfeditor.data.SignatureLibrary
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfEditorApp : Application() {
    lateinit var recents: RecentFilesRepository
        private set
    lateinit var signatures: SignatureLibrary
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        recents = RecentFilesRepository(this)
        signatures = SignatureLibrary(this)
    }
}
