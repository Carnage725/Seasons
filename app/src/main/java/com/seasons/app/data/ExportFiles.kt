package com.seasons.app.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Writes export files to a private cache folder and hands them to the system share sheet. */
object ExportFiles {
    private const val DIR = "exports"

    /** Replaces any earlier export, so old copies of your data do not pile up in the cache. */
    fun write(context: Context, fileName: String, text: String): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, fileName).also { it.writeText(text, Charsets.UTF_8) }
    }

    fun share(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name)
            // The clip makes the read permission cover the share sheet's own preview too.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }
}
