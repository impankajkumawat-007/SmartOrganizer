package com.smartorganizer.agent.files

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.smartorganizer.agent.data.DocumentCategory

class InboxScanner(private val context: Context) {
    fun listInbox(treeUri: Uri): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val skip = DocumentCategory.entries.map { it.folderName.lowercase() }.toSet()
        val now = System.currentTimeMillis()
        return root.listFiles()
            .filter { file ->
                file.isFile &&
                    !file.name.isNullOrBlank() &&
                    !file.name.orEmpty().startsWith(".") &&
                    file.name?.substringAfterLast('.', "")?.lowercase() !in IN_PROGRESS &&
                    file.length() > 0L &&
                    now - file.lastModified() > STABLE_AFTER_MS
            }
            .filter { file ->
                val parent = file.parentFile
                parent == null || parent.uri == root.uri
            }
            .filter { file ->
                val name = file.name.orEmpty().lowercase()
                skip.none { name == it }
            }
    }

    fun root(treeUri: Uri): DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

    companion object {
        private const val STABLE_AFTER_MS = 2_500L
        private val IN_PROGRESS = setOf("tmp", "part", "crdownload", "download")
    }
}
