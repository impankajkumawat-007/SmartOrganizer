package com.smartorganizer.agent.llm

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.ensureActive
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.coroutineContext

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Progress(val bytes: Long, val total: Long) : DownloadStatus
    data class Ready(val path: String) : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}

class ModelDownloader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val cancelRequested = AtomicBoolean(false)

    fun modelFile(): File = File(File(context.filesDir, "models").apply { mkdirs() }, ModelCatalog.FILE_NAME)

    fun partialFile(): File = File(modelFile().parentFile, "${ModelCatalog.FILE_NAME}.part")

    fun currentStatus(): DownloadStatus {
        val file = modelFile()
        return if (file.exists() && ModelCatalog.isComplete(file.length())) {
            DownloadStatus.Ready(file.absolutePath)
        } else {
            DownloadStatus.Idle
        }
    }

    fun cancel() {
        cancelRequested.set(true)
    }

    fun download(token: String): Flow<DownloadStatus> = flow {
        cancelRequested.set(false)
        val destination = modelFile()
        if (destination.exists() && ModelCatalog.isComplete(destination.length())) {
            emit(DownloadStatus.Ready(destination.absolutePath))
            return@flow
        }

        val part = partialFile()
        var downloaded = if (part.exists()) part.length() else 0L
        val requestBuilder = Request.Builder()
            .url(ModelCatalog.DOWNLOAD_URL)
            .header("User-Agent", "SmartOrganizer/1.0")
            .header("Accept", "*/*")
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        if (downloaded > 0) {
            requestBuilder.header("Range", "bytes=$downloaded-")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (response.code == 401 || response.code == 403) {
            response.close()
            emit(
                DownloadStatus.Failed(
                    "Hugging Face blocked the download. Accept the Gemma license on the model page, then paste an access token.",
                ),
            )
            return@flow
        }
        if (!response.isSuccessful && response.code != 206) {
            val code = response.code
            response.close()
            emit(DownloadStatus.Failed("Download failed (HTTP $code)."))
            return@flow
        }

        val totalFromHeader = response.header("Content-Range")
            ?.substringAfter("/")
            ?.toLongOrNull()
            ?: response.body?.contentLength()?.let { length ->
                if (response.code == 206) downloaded + length else length
            }
            ?: ModelCatalog.EXPECTED_BYTES

        if (response.code != 206) {
            downloaded = 0L
            if (part.exists()) part.delete()
        }

        val body = response.body ?: run {
            response.close()
            emit(DownloadStatus.Failed("Empty response from Hugging Face."))
            return@flow
        }

        body.byteStream().use { input ->
            FileOutputStream(part, downloaded > 0).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                emit(DownloadStatus.Progress(downloaded, totalFromHeader))
                while (true) {
                    coroutineContext.ensureActive()
                    if (cancelRequested.get()) {
                        emit(DownloadStatus.Failed("Download cancelled."))
                        return@flow
                    }
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    emit(DownloadStatus.Progress(downloaded, totalFromHeader))
                }
                output.flush()
            }
        }
        response.close()

        if (!ModelCatalog.isComplete(part.length())) {
            emit(
                DownloadStatus.Failed(
                    "Downloaded ${part.length()} bytes, expected ${ModelCatalog.EXPECTED_BYTES}. Try again to resume.",
                ),
            )
            return@flow
        }

        if (destination.exists()) destination.delete()
        if (!part.renameTo(destination)) {
            part.copyTo(destination, overwrite = true)
            part.delete()
        }
        emit(DownloadStatus.Ready(destination.absolutePath))
    }.flowOn(Dispatchers.IO)

    fun importFrom(uri: Uri): Flow<DownloadStatus> = flow {
        cancelRequested.set(false)
        val destination = modelFile()
        val part = partialFile()
        if (part.exists()) part.delete()

        val resolver = context.contentResolver
        val declaredSize = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        resolver.openInputStream(uri)?.use { input ->
            copyStream(input, part, declaredSize.coerceAtLeast(0L)) { bytes, total ->
                emit(DownloadStatus.Progress(bytes, total))
            }
        } ?: run {
            emit(DownloadStatus.Failed("Could not open the selected model file."))
            return@flow
        }

        if (part.length() < 100_000_000L) {
            part.delete()
            emit(DownloadStatus.Failed("That file is too small to be Gemma 4 E2B."))
            return@flow
        }
        if (destination.exists()) destination.delete()
        if (!part.renameTo(destination)) {
            part.copyTo(destination, overwrite = true)
            part.delete()
        }
        emit(DownloadStatus.Ready(destination.absolutePath))
    }.flowOn(Dispatchers.IO)

    private suspend fun copyStream(
        input: InputStream,
        destination: File,
        totalHint: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            onProgress(0L, totalHint)
            while (true) {
                coroutineContext.ensureActive()
                if (cancelRequested.get()) error("Import cancelled.")
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                copied += read
                onProgress(copied, if (totalHint > 0) totalHint else copied)
            }
            output.flush()
        }
    }
}
