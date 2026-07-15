package com.pdfvault.desktop.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.File

/**
 * Thin wrapper over Apache PDFBox for the desktop reader. PDFBox renders pages to
 * [BufferedImage] on the JVM (no Android PdfRenderer). Not thread-safe, so rendering is
 * serialised via [synchronized].
 */
class PdfDocument(val file: File) : Closeable {

    private val document: PDDocument = PDDocument.load(file)
    private val renderer = PDFRenderer(document)

    // Precomputed at load (cropBox reads only, no rendering) so layout can query page sizes
    // without taking the render lock — a UI-thread caller must never wait on an in-flight render.
    private val aspectRatios: FloatArray = FloatArray(document.numberOfPages) { i ->
        val box = document.getPage(i).cropBox
        if (box.height > 0f) box.width / box.height else 0.707f
    }

    val pageCount: Int get() = document.numberOfPages

    /** Displayed width/height ratio of page [index], accounting for a user [rotation] (0/90/180/270). */
    fun pageAspectRatio(index: Int, rotation: Int = 0): Float {
        val aspect = aspectRatios.getOrElse(index) { 0.707f }
        return if (rotation == 90 || rotation == 270) {
            if (aspect > 0f) 1f / aspect else 1.414f
        } else {
            aspect
        }
    }

    /** Renders page [index] at [dpi] (72 = 100%). */
    @Synchronized
    fun renderPage(index: Int, dpi: Float): BufferedImage =
        renderer.renderImageWithDPI(index, dpi, ImageType.RGB)

    /**
     * Renders page [index] so the final (optionally [rotation]-rotated) image is about
     * [targetWidthPx] wide, preserving aspect ratio. Used by the continuous reader so every page
     * lays out to the same on-screen width regardless of its intrinsic size.
     */
    @Synchronized
    fun renderPageToWidth(index: Int, targetWidthPx: Int, rotation: Int = 0): BufferedImage {
        val box = document.getPage(index).cropBox
        // When sideways, the on-screen width comes from the page's point height.
        val displayWidthPoints = (if (rotation == 90 || rotation == 270) box.height else box.width)
            .takeIf { it > 0f } ?: 612f
        val dpi = (targetWidthPx * 72f / displayWidthPoints).coerceIn(12f, 600f)
        val image = renderer.renderImageWithDPI(index, dpi, ImageType.RGB)
        return rotate(image, rotation)
    }

    private fun rotate(src: BufferedImage, degrees: Int): BufferedImage {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return src
        val (nw, nh) = if (d == 180) src.width to src.height else src.height to src.width
        val dst = BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        val at = java.awt.geom.AffineTransform()
        when (d) {
            90 -> { at.translate(nw.toDouble(), 0.0); at.rotate(Math.toRadians(90.0)) }
            180 -> { at.translate(nw.toDouble(), nh.toDouble()); at.rotate(Math.toRadians(180.0)) }
            270 -> { at.translate(0.0, nh.toDouble()); at.rotate(Math.toRadians(270.0)) }
        }
        g.drawImage(src, at, null)
        g.dispose()
        return dst
    }

    override fun close() {
        document.close()
    }
}
