package com.smartorganizer.agent.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class FilePreview(
    val displayName: String,
    val mimeType: String,
    val imagePaths: List<String>,
    val extractedText: String,
) {
    fun cleanup() {
        imagePaths.forEach { File(it).delete() }
    }
}

class DocumentPreview(private val context: Context) {
    fun prepare(document: DocumentFile): FilePreview {
        val name = document.name ?: "unnamed"
        val mime = document.type
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', ""))
            ?: "application/octet-stream"
        val workDir = File(context.cacheDir, "previews").apply { mkdirs() }
        val source = copyToCache(document, File(workDir, "source-${System.currentTimeMillis()}-$name"))

        return try {
            when {
                mime.startsWith("image/") -> {
                    val image = renderImage(source, File(workDir, "page-${System.nanoTime()}.png"))
                    FilePreview(name, mime, listOfNotNull(image), "")
                }
                mime == "application/pdf" || name.endsWith(".pdf", true) -> {
                    val pages = renderPdf(source, workDir)
                    FilePreview(name, "application/pdf", pages, "")
                }
                mime.startsWith("text/") || TEXT_EXTENSIONS.any { name.endsWith(it, true) } -> {
                    FilePreview(name, mime, emptyList(), source.readText().take(2500))
                }
                else -> FilePreview(name, mime, emptyList(), "")
            }
        } finally {
            source.delete()
        }
    }

    private fun copyToCache(document: DocumentFile, destination: File): File {
        context.contentResolver.openInputStream(document.uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read ${document.name}")
        return destination
    }

    private fun renderImage(source: File, destination: File): String? {
        val original = decodeDownsampled(source) ?: return null
        writePng(original, destination)
        original.recycle()
        return destination.absolutePath
    }

    private fun renderPdf(source: File, workDir: File): List<String> {
        val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor).use { renderer ->
            val count = minOf(renderer.pageCount, 2)
            (0 until count).mapNotNull { index ->
                renderer.openPage(index).use { page ->
                    val scale = minOf(1024f / max(page.width, 1), 1024f / max(page.height, 1), 2f)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = File(workDir, "pdf-${System.nanoTime()}-$index.png")
                    writePng(bitmap, out)
                    bitmap.recycle()
                    out.absolutePath
                }
            }
        }.also {
            descriptor.close()
        }
    }

    private fun decodeDownsampled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = max(1, max(bounds.outWidth, bounds.outHeight) / 1024)
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun writePng(bitmap: Bitmap, destination: File) {
        FileOutputStream(destination).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    companion object {
        private val TEXT_EXTENSIONS = listOf(".txt", ".md", ".csv", ".json", ".xml", ".log")
    }
}
