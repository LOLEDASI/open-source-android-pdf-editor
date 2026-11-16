package com.mobile.pdfeditor.data

import java.net.URLDecoder

/** Pure helpers for naming and finding imported PDFs. No Android dependencies, so unit-testable. */
object ImportNames {
    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val illegal = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    /** First http(s) URL in shared text such as "Q3 report: https://x.com/r.pdf". */
    fun extractUrl(text: String): String? = extractUrls(text).firstOrNull()

    fun extractUrls(text: String): List<String> =
        urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ')', ']', ';', '!', '?', '*', '_') }.toList()

    /**
     * The link most likely to be the PDF in shared text. Apps such as Finqalab share messages with
     * several links (e.g. a notice image and the notice PDF), so prefer one whose path ends in
     * .pdf, then one that mentions pdf/document, and skip obvious images.
     */
    fun pdfUrl(text: String): String? {
        val urls = extractUrls(text)
        fun path(u: String) = u.substringBefore('?').substringBefore('#').lowercase()
        val images = listOf(".gif", ".png", ".jpg", ".jpeg", ".webp", ".svg", ".bmp")
        return urls.firstOrNull { path(it).endsWith(".pdf") }
            ?: urls.firstOrNull { u -> u.lowercase().let { "pdf" in it || "document" in it } && images.none { path(u).endsWith(it) } }
            ?: urls.firstOrNull { u -> images.none { path(u).endsWith(it) } }
            ?: urls.firstOrNull()
    }

    /**
     * A file name for a downloaded PDF: Content-Disposition's filename if present,
     * else the URL's last path segment, else "Document.pdf". Always ends in .pdf.
     */
    fun nameForDownload(url: String, contentDisposition: String?): String {
        val fromHeader = contentDisposition?.let { cd ->
            Regex("""filename\*=(?:UTF-8'')?([^;]+)""", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)
                ?.let { decode(it.trim().trim('"')) }
                ?: Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)?.trim()
        }
        val path = try {
            java.net.URI(url).rawPath.orEmpty()
        } catch (_: Exception) {
            url.substringAfter("://", "").substringAfter('/', "").substringBefore('?').substringBefore('#')
        }
        val fromUrl = path.trimEnd('/').substringAfterLast('/')
            .takeIf { it.isNotBlank() }
            ?.let(::decode)
        return pdfName(fromHeader ?: fromUrl)
    }

    /** Cleans [raw] into a safe file name that ends in .pdf. */
    fun pdfName(raw: String?): String {
        val base = raw?.replace(illegal, "_")?.trim()?.trim('.')?.take(120).orEmpty()
        if (base.isBlank()) return "Document.pdf"
        return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
    }

    /** True if [bytes] start with the PDF signature (allowing a little junk before it). */
    fun looksLikePdf(bytes: ByteArray, length: Int = bytes.size): Boolean {
        val window = String(bytes, 0, minOf(length, 1024), Charsets.ISO_8859_1)
        return window.contains("%PDF-")
    }

    private fun decode(s: String) = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        s
    }
}
