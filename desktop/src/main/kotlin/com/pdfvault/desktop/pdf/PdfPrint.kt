package com.pdfvault.desktop.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.printing.PDFPageable
import java.awt.print.PrinterJob
import java.io.File

/**
 * Opens the native print dialog for [file] and prints it if the user confirms. Uses PDFBox's
 * [PDFPageable]. Blocking (shows a modal dialog + prints), so call it off the UI thread.
 */
fun printPdf(file: File) {
    runCatching {
        PDDocument.load(file).use { doc ->
            val job = PrinterJob.getPrinterJob()
            job.setPageable(PDFPageable(doc))
            if (job.printDialog()) job.print()
        }
    }
}
