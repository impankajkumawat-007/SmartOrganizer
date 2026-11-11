package com.smartorganizer.agent.files

import com.smartorganizer.agent.data.DocumentCategory
import com.smartorganizer.agent.llm.Classification

object NoTextFallback {
    fun classify(fileName: String, mimeType: String): Classification {
        val name = fileName.lowercase()
        val category = when {
            "screenshot" in name -> DocumentCategory.SCREENSHOT
            mimeType.startsWith("image/") -> DocumentCategory.PHOTO
            else -> DocumentCategory.OTHER
        }
        return Classification(
            category = category,
            confidence = 0.5f,
            reason = "ML Kit found no readable text; sorted from the file name.",
            raw = "",
        )
    }
}
