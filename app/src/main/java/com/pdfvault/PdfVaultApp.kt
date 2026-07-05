package com.pdfvault

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PdfVaultApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // PdfBox (used only to read the table-of-contents outline) needs one-time init.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
