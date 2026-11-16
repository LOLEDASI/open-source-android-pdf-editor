package com.mobile.pdfeditor.pdf

import com.mobile.pdfeditor.pages.PagePlan
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File

/** Rebuilds a PDF's page list from a [PagePlan]: reorder, rotate, duplicate, delete, insert blanks. */
object PageOps {
    fun apply(input: File, plan: PagePlan, output: File) {
        PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            val originals = doc.pages.toList()

            // Pages can inherit resources, boxes and rotation from parent nodes in the page tree.
            // Copy those onto each page first, since the tree is about to be rebuilt.
            originals.forEach { p ->
                p.resources = p.resources
                p.mediaBox = p.mediaBox
                p.cropBox = p.cropBox
                p.rotation = p.rotation
            }

            // Build every page object before changing any, so copies start from the original.
            val used = HashSet<Int>()
            val built = plan.pages.map { entry ->
                when {
                    entry.isBlank -> PDPage(PDRectangle(entry.blankWidth, entry.blankHeight))
                    used.add(entry.source) -> originals[entry.source]
                    else -> clone(originals[entry.source])
                }
            }
            val baseRotation = built.map { it.rotation }
            built.forEachIndexed { i, page ->
                page.rotation = (baseRotation[i] + plan.pages[i].rotation) % 360
            }

            val tree = doc.pages
            originals.forEach { tree.remove(it) }
            built.forEach { tree.add(it) }
            doc.save(output)
        }
    }

    /** A second page sharing content and resources, with its own copies of the annotations. */
    private fun clone(page: PDPage): PDPage {
        val dict = COSDictionary(page.cosObject)
        dict.removeItem(COSName.PARENT)
        page.cosObject.getCOSArray(COSName.ANNOTS)?.let { annots ->
            val copies = COSArray()
            for (i in 0 until annots.size()) {
                val a = annots.getObject(i) as? COSDictionary ?: continue
                copies.add(COSDictionary(a).apply { removeItem(COSName.P) })
            }
            dict.setItem(COSName.ANNOTS, copies)
        }
        return PDPage(dict)
    }
}
