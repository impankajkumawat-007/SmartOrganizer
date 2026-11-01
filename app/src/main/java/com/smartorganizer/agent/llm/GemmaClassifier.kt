package com.smartorganizer.agent.llm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.smartorganizer.agent.data.DocumentCategory
import java.io.File
import org.json.JSONObject

data class Classification(
    val category: DocumentCategory,
    val confidence: Float,
    val reason: String,
    val raw: String,
)

class GemmaClassifier {
    private var engine: Engine? = null
    private var visionEnabled: Boolean = false

    val isReady: Boolean get() = engine != null

    fun load(modelPath: String, cacheDir: String) {
        close()
        val attempts = listOf(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                visionBackend = Backend.GPU(),
                maxNumTokens = 4096,
                maxNumImages = 2,
                cacheDir = cacheDir,
            ),
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                visionBackend = Backend.GPU(),
                maxNumTokens = 4096,
                maxNumImages = 2,
                cacheDir = cacheDir,
            ),
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                visionBackend = Backend.CPU(),
                maxNumTokens = 4096,
                maxNumImages = 2,
                cacheDir = cacheDir,
            ),
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                maxNumTokens = 4096,
                cacheDir = cacheDir,
            ),
        )

        var lastError: Exception? = null
        for (config in attempts) {
            val candidate = Engine(config)
            try {
                candidate.initialize()
                engine = candidate
                visionEnabled = config.visionBackend != null
                return
            } catch (error: Exception) {
                lastError = error
                runCatching { candidate.close() }
            }
        }
        throw lastError ?: IllegalStateException("Could not initialize Gemma 4 E2B.")
    }

    fun classify(
        fileName: String,
        mimeType: String,
        imagePaths: List<String>,
        extractedText: String,
    ): Classification {
        val current = engine ?: error("Gemma 4 is not loaded yet.")
        val contents = buildContents(fileName, mimeType, imagePaths, extractedText)
        val conversation = current.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(SYSTEM_PROMPT),
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                maxOutputToken = 96,
                thinkingConfig = ThinkingConfig(enableThinking = false, thinkingTokenBudget = 0),
            ),
        )
        return try {
            val response = conversation.sendMessage(contents)
            parse(response.toString())
        } finally {
            runCatching { conversation.close() }
        }
    }

    fun close() {
        val current = engine
        engine = null
        visionEnabled = false
        if (current != null) {
            runCatching { current.close() }
        }
    }

    private fun buildContents(
        fileName: String,
        mimeType: String,
        imagePaths: List<String>,
        extractedText: String,
    ): Contents {
        val prompt = buildString {
            appendLine("Classify this file into exactly one category.")
            appendLine("File name: $fileName")
            appendLine("MIME type: ${mimeType.ifBlank { "unknown" }}")
            if (extractedText.isNotBlank()) {
                appendLine("Extracted text:")
                appendLine(extractedText.take(2500))
            }
            if (imagePaths.isNotEmpty()) {
                appendLine("One or more page/photo images are attached.")
            }
            appendLine("Reply with JSON only.")
        }

        val parts = buildList {
            if (visionEnabled) {
                imagePaths.take(2).mapNotNull { path ->
                    path.takeIf { File(it).exists() }?.let { Content.ImageFile(it) }
                }.forEach { add(it) }
            }
            add(Content.Text(prompt))
        }
        return Contents.of(*parts.toTypedArray())
    }

    private fun parse(raw: String): Classification {
        val jsonText = raw
            .substringAfter("{", missingDelimiterValue = "")
            .substringBeforeLast("}", missingDelimiterValue = "")
            .let { if (it.isBlank()) raw else "{$it}" }

        val parsed = runCatching { JSONObject(jsonText) }.getOrNull()
        val category = DocumentCategory.fromModelLabel(
            parsed?.optString("folder")?.ifBlank { parsed.optString("category") },
        )
        val confidence = parsed?.optDouble("confidence", 0.6)?.toFloat()?.coerceIn(0f, 1f) ?: 0.55f
        val reason = parsed?.optString("reason").orEmpty().ifBlank { "Gemma 4 classification" }
        val finalCategory = if (confidence < 0.45f) DocumentCategory.REVIEW else category
        return Classification(finalCategory, confidence, reason, raw)
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are an on-device document organizer. Look at the file name, any attached images, and any extracted text.
            Choose exactly one folder from: Resume, Ticket, Bill, ID, Notes, Screenshot, Photo, Other.

            Rules:
            - Resume: CV, curriculum vitae, job application.
            - Ticket: boarding pass, train/bus/movie/event ticket, e-ticket.
            - Bill: invoice, receipt, utility bill, tax invoice.
            - ID: passport, Aadhaar, PAN, driving licence, identity card.
            - Notes: handwritten or typed notes, meeting notes.
            - Screenshot: phone or computer screenshot.
            - Photo: a personal camera photo that is not a document.
            - Other: anything else.

            A ticket photo and a ticket PDF both go to Ticket. A resume PDF goes to Resume.
            Reply with JSON only, no markdown:
            {"folder":"Ticket","confidence":0.86,"reason":"boarding pass"}
        """.trimIndent()
    }
}
