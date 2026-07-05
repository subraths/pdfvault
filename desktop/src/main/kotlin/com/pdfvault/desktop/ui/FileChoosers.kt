package com.pdfvault.desktop.ui

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Native file dialogs (AWT/Swing) for picking PDFs to upload and a save location. */
object FileChoosers {

    fun openPdfs(): List<File> {
        val chooser = JFileChooser().apply {
            isMultiSelectionEnabled = true
            fileFilter = FileNameExtensionFilter("PDF files", "pdf")
            dialogTitle = "Choose PDFs to upload"
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFiles.toList()
        } else {
            emptyList()
        }
    }

    fun savePath(defaultName: String): File? {
        val chooser = JFileChooser().apply {
            selectedFile = File(defaultName)
            dialogTitle = "Save a copy"
        }
        return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /** Picks a single local PDF to open in the reader. */
    fun openSinglePdf(): File? {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("PDF files", "pdf")
            dialogTitle = "Open PDF"
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /** Picks a destination folder for batch downloads. */
    fun chooseDirectory(): File? {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose a download folder"
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}
