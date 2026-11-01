package com.smartorganizer.agent.files

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.smartorganizer.agent.data.DocumentCategory
import java.io.IOException

class FileOrganizer(private val context: Context) {
    fun moveToCategory(root: DocumentFile, source: DocumentFile, category: DocumentCategory): DocumentFile {
        val folder = root.findFile(category.folderName)?.takeIf { it.isDirectory }
            ?: root.createDirectory(category.folderName)
            ?: throw IOException("Could not create ${category.folderName}")

        val originalName = source.name ?: "file"
        val destinationName = uniqueName(folder, originalName)
        val mime = source.type ?: "application/octet-stream"
        val destination = folder.createFile(mime, destinationName)
            ?: throw IOException("Could not create $destinationName in ${category.folderName}")

        context.contentResolver.openInputStream(source.uri)?.use { input ->
            context.contentResolver.openOutputStream(destination.uri, "w")?.use { output ->
                input.copyTo(output)
            } ?: throw IOException("Could not write $destinationName")
        } ?: throw IOException("Could not read ${source.name}")

        if (!source.delete()) {
            throw IOException("Copied ${source.name} but could not remove the original inbox file.")
        }
        return destination
    }

    private fun uniqueName(folder: DocumentFile, originalName: String): String {
        if (folder.findFile(originalName) == null) return originalName
        val stem = originalName.substringBeforeLast('.', originalName)
        val extension = originalName.substringAfterLast('.', missingDelimiterValue = "")
        var index = 1
        while (true) {
            val candidate = if (extension.isBlank()) "${stem}_$index" else "${stem}_$index.$extension"
            if (folder.findFile(candidate) == null) return candidate
            index += 1
        }
    }
}
