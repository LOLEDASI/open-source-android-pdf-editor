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
