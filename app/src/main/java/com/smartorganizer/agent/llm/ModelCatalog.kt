package com.smartorganizer.agent.llm

data class OrganizerModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val expectedBytes: Long,
    val displaySize: String,
    val downloadUrl: String,
    val supportsVision: Boolean,
    val summary: String,
) {
    fun isComplete(size: Long): Boolean = size == expectedBytes
}

object ModelCatalog {
    val GEMMA_4_E2B = OrganizerModel(
        id = "gemma-4-e2b",
        displayName = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        expectedBytes = 2_588_147_712L,
        displaySize = "2.6 GB",
        downloadUrl =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        supportsVision = true,
        summary = "Looks at the page. Best for tickets, IDs, and photos with little text.",
    )

    val GEMMA_3_1B = OrganizerModel(
        id = "gemma-3-1b",
        displayName = "Gemma 3 1B",
        fileName = "gemma3-1b-it-int4.litertlm",
        expectedBytes = 584_417_280L,
        displaySize = "557 MB",
        downloadUrl =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
        supportsVision = false,
        summary = "Text only. ML Kit reads the page, then this model picks the folder.",
    )

    val QWEN_3_06B = OrganizerModel(
        id = "qwen-3-0.6b",
        displayName = "Qwen 3 0.6B",
        fileName = "Qwen3-0.6B.litertlm",
        expectedBytes = 613_722_264L,
        displaySize = "585 MB",
        downloadUrl =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        supportsVision = false,
        summary = "Smallest text model. ML Kit reads the page first. Stronger on mixed Hindi/English.",
    )

    val all: List<OrganizerModel> = listOf(GEMMA_4_E2B, GEMMA_3_1B, QWEN_3_06B)

    fun byId(id: String?): OrganizerModel =
        all.firstOrNull { it.id == id } ?: GEMMA_4_E2B
}
