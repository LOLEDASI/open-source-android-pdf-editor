package com.mobile.pdfeditor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportNamesTest {
    @Test
    fun findsUrlInSharedText() {
        assertEquals(
            "https://cdn.example.com/reports/Q3-2026.pdf",
            ImportNames.extractUrl("Check the Q3 report: https://cdn.example.com/reports/Q3-2026.pdf."),
        )
        assertEquals("http://x.io/a?b=1&c=2", ImportNames.extractUrl("(http://x.io/a?b=1&c=2)"))
        assertNull(ImportNames.extractUrl("no link here"))
    }

    @Test
    fun picksThePdfFromAFinqalabNotice() {
        val shared = """
            *MEBL notices update from Finqalab*
            Oct 28, 2026 • 11:14 AM

            TRANSMISSION OF QUARTERLY REPORT OF MEEZAN BANK LIMITED FOR THE PERIOD ENDED SEPTEMBER 30, 2026

            View the notice image: https://dps.psx.com.pk/download/image/287198-1.gif

            Download the notice PDF: https://dps.psx.com.pk/download/document/287198.pdf
        """.trimIndent()
        assertEquals("https://dps.psx.com.pk/download/document/287198.pdf", ImportNames.pdfUrl(shared))
    }

    @Test
    fun pdfUrlFallsBackSensibly() {
        // No .pdf ending: prefer a document-looking link over an image.
        assertEquals(
            "https://x.com/download/document/42",
            ImportNames.pdfUrl("img https://x.com/a.png doc https://x.com/download/document/42"),
        )
        assertEquals("https://x.com/report", ImportNames.pdfUrl("https://x.com/pic.jpg and https://x.com/report"))
        assertEquals("https://x.com/only.gif", ImportNames.pdfUrl("https://x.com/only.gif"))
        assertNull(ImportNames.pdfUrl("no links"))
    }

    @Test
    fun nameFromContentDisposition() {
        assertEquals("Annual Report 2025.pdf", ImportNames.nameForDownload("https://x.com/dl?id=9", "attachment; filename=\"Annual Report 2025.pdf\""))
        assertEquals("Résumé.pdf", ImportNames.nameForDownload("https://x.com/dl", "attachment; filename*=UTF-8''R%C3%A9sum%C3%A9.pdf"))
    }

    @Test
    fun nameFromUrlPath() {
        assertEquals("Q3 report.pdf", ImportNames.nameForDownload("https://x.com/files/Q3%20report.pdf?token=abc", null))
        assertEquals("download.pdf", ImportNames.nameForDownload("https://x.com/download", null))
        assertEquals("Document.pdf", ImportNames.nameForDownload("https://x.com/", null))
    }

    @Test
    fun cleansUnsafeNames() {
        assertEquals("a_b_c.pdf", ImportNames.pdfName("a/b:c"))
        assertEquals("Document.pdf", ImportNames.pdfName("  "))
        assertEquals("Report.PDF", ImportNames.pdfName("Report.PDF"))
    }

    @Test
    fun detectsPdfSignature() {
        assertTrue(ImportNames.looksLikePdf("%PDF-1.7\n...".toByteArray()))
        assertTrue(ImportNames.looksLikePdf("﻿\n%PDF-1.4".toByteArray()))
        assertFalse(ImportNames.looksLikePdf("<!doctype html><html>".toByteArray()))
    }
}
