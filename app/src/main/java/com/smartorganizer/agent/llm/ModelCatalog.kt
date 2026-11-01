package com.smartorganizer.agent.llm

object ModelCatalog {
    const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    const val EXPECTED_BYTES = 2_588_147_712L
    const val DISPLAY_SIZE = "2.6 GB"
    const val DOWNLOAD_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

    fun isComplete(size: Long): Boolean = size == EXPECTED_BYTES
}
