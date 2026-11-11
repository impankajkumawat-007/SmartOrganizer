package com.smartorganizer.agent.runtime

import com.smartorganizer.agent.data.OrganizedFile
import com.smartorganizer.agent.llm.ModelCatalog
import com.smartorganizer.agent.llm.OrganizerModel

data class OrganizerUiState(
    val selectedModel: OrganizerModel = ModelCatalog.GEMMA_4_E2B,
    val modelReady: Boolean = false,
    val engineReady: Boolean = false,
    val engineBusy: Boolean = false,
    val downloading: Boolean = false,
    val downloadBytes: Long = 0L,
    val downloadTotal: Long = ModelCatalog.GEMMA_4_E2B.expectedBytes,
    val watchFolderUri: String? = null,
    val watchFolderName: String? = null,
    val watching: Boolean = false,
    val processingName: String? = null,
    val huggingFaceToken: String = "",
    val statusMessage: String = "Pick a model, download it once, then sorting stays offline.",
    val errorMessage: String? = null,
    val recentFiles: List<OrganizedFile> = emptyList(),
) {
    val downloadFraction: Float =
        if (downloadTotal <= 0L) 0f else (downloadBytes.toFloat() / downloadTotal.toFloat()).coerceIn(0f, 1f)
}
