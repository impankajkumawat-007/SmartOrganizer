package com.smartorganizer.agent.runtime

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.smartorganizer.agent.data.AppDatabase
import com.smartorganizer.agent.data.FailedFile
import com.smartorganizer.agent.data.OrganizedFile
import com.smartorganizer.agent.data.UserPreferences
import java.util.concurrent.ConcurrentHashMap
import com.smartorganizer.agent.files.DocumentPreview
import com.smartorganizer.agent.files.FileOrganizer
import com.smartorganizer.agent.files.InboxScanner
import com.smartorganizer.agent.files.NoTextFallback
import com.smartorganizer.agent.llm.DownloadStatus
import com.smartorganizer.agent.llm.GemmaClassifier
import com.smartorganizer.agent.llm.ModelCatalog
import com.smartorganizer.agent.llm.ModelDownloader
import com.smartorganizer.agent.llm.OrganizerModel
import com.smartorganizer.agent.service.OrganizerService
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OrganizerRuntime(private val app: Application) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val llmExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gemma-llm").apply { isDaemon = true }
    }
    private val llmDispatcher = llmExecutor.asCoroutineDispatcher()
    private val llmScope = CoroutineScope(SupervisorJob() + llmDispatcher)
    private val llmMutex = Mutex()

    private val preferences = UserPreferences(app)
    private val database = AppDatabase.create(app)
    private val downloader = ModelDownloader(app)
    private val classifier = GemmaClassifier()
    private val previewer = DocumentPreview(app)
    private val scanner = InboxScanner(app)
    private val organizer = FileOrganizer(app)
    private val skippedUris = ConcurrentHashMap.newKeySet<String>()

    private val _uiState = MutableStateFlow(OrganizerUiState())
    val uiState: StateFlow<OrganizerUiState> = _uiState.asStateFlow()

    init {
        ioScope.launch {
            preferences.selectedModelId.collect { id ->
                val model = ModelCatalog.byId(id)
                _uiState.update { it.copy(selectedModel = model, downloadTotal = model.expectedBytes) }
                refreshModelStatus()
            }
        }
        ioScope.launch {
            preferences.watchFolderUri.collect { uri ->
                val name = uri?.let { DocumentFile.fromTreeUri(app, Uri.parse(it))?.name }
                _uiState.update { it.copy(watchFolderUri = uri, watchFolderName = name) }
            }
        }
        ioScope.launch {
            preferences.huggingFaceToken.collect { token ->
                _uiState.update { it.copy(huggingFaceToken = token) }
            }
        }
        ioScope.launch {
            database.organizedFileDao().observeAll().collect { files ->
                _uiState.update { it.copy(recentFiles = files) }
            }
        }
        ioScope.launch {
            skippedUris.addAll(database.failedFileDao().uris())
        }
    }

    fun refreshModelStatus() {
        val model = _uiState.value.selectedModel
        when (val status = downloader.currentStatus(model)) {
            is DownloadStatus.Ready -> _uiState.update {
                it.copy(
                    modelReady = true,
                    statusMessage = "${model.displayName} is on this phone. Start watching to load it.",
                )
            }
            else -> _uiState.update {
                it.copy(
                    modelReady = false,
                    engineReady = false,
                    statusMessage = "Download ${model.displayName} once (${model.displaySize}). ML Kit reads text; the model picks the folder.",
                )
            }
        }
    }

    fun setSelectedModel(model: OrganizerModel) {
        if (_uiState.value.downloading) return
        if (_uiState.value.watching) {
            recordFailure("Stop watching before switching models.")
            return
        }
        ioScope.launch {
            withContext(llmDispatcher) {
                llmMutex.withLock { classifier.close() }
            }
            preferences.setSelectedModelId(model.id)
            _uiState.update {
                it.copy(
                    selectedModel = model,
                    engineReady = false,
                    downloadTotal = model.expectedBytes,
                    errorMessage = null,
                )
            }
            refreshModelStatus()
        }
    }

    fun setHuggingFaceToken(token: String) {
        ioScope.launch { preferences.setHuggingFaceToken(token) }
        _uiState.update { it.copy(huggingFaceToken = token) }
    }

    fun setWatchFolder(uri: Uri) {
        ioScope.launch { preferences.setWatchFolderUri(uri.toString()) }
    }

    fun startDownload() {
        if (_uiState.value.downloading) return
        val model = _uiState.value.selectedModel
        _uiState.update {
            it.copy(
                downloading = true,
                errorMessage = null,
                downloadTotal = model.expectedBytes,
                statusMessage = "Downloading ${model.displayName}…",
            )
        }
        ioScope.launch {
            downloader.download(model, _uiState.value.huggingFaceToken).collect { status ->
                handleDownloadStatus(model, status)
            }
        }
    }

    fun importModel(uri: Uri) {
        if (_uiState.value.downloading) return
        val model = _uiState.value.selectedModel
        _uiState.update {
            it.copy(
                downloading = true,
                errorMessage = null,
                downloadTotal = model.expectedBytes,
                statusMessage = "Copying ${model.displayName} onto the phone…",
            )
        }
        ioScope.launch {
            runCatching {
                downloader.importFrom(model, uri).collect { status -> handleDownloadStatus(model, status) }
            }.onFailure { error ->
                _uiState.update { it.copy(downloading = false) }
                recordFailure("Could not import ${model.displayName}", error)
            }
        }
    }

    fun cancelDownload() {
        downloader.cancel()
    }

    fun startWatching() {
        val state = _uiState.value
        if (!state.modelReady) {
            recordFailure("Download ${state.selectedModel.displayName} first.")
            return
        }
        if (state.watchFolderUri.isNullOrBlank()) {
            recordFailure("Pick the inbox folder to watch.")
            return
        }
        OrganizerService.start(app)
        _uiState.update { it.copy(watching = true, errorMessage = null) }
        ensureEngineLoaded()
    }

    fun stopWatching() {
        OrganizerService.stop(app)
        _uiState.update { it.copy(watching = false, processingName = null, statusMessage = "Watching paused.") }
    }

    fun setWatchingFromService(watching: Boolean) {
        _uiState.update { it.copy(watching = watching) }
    }

    fun recordFailure(message: String, error: Throwable? = null) {
        val detail = buildString {
            append(message)
            val cause = error?.message?.trim().orEmpty()
            if (cause.isNotBlank() && !message.contains(cause)) {
                append(" — ")
                append(cause)
            }
        }
        _uiState.update {
            it.copy(
                engineBusy = false,
                processingName = null,
                errorMessage = detail,
            )
        }
    }

    private suspend fun skipFailedFile(uri: String, fileName: String, error: Throwable) {
        skippedUris.add(uri)
        runCatching {
            database.failedFileDao().upsert(
                FailedFile(
                    uri = uri,
                    fileName = fileName,
                    error = error.message.orEmpty(),
                    failedAt = System.currentTimeMillis(),
                ),
            )
        }
        recordFailure("Could not organize $fileName", error)
    }

    fun ensureEngineLoaded() {
        llmScope.launch {
            llmMutex.withLock {
                val model = _uiState.value.selectedModel
                val path = downloader.modelFile(model).absolutePath
                if (classifier.isLoaded(path)) {
                    _uiState.update { it.copy(engineReady = true) }
                    return@withLock
                }
                _uiState.update {
                    it.copy(engineBusy = true, statusMessage = "Loading ${model.displayName} into memory…")
                }
                try {
                    classifier.load(model, path, app.cacheDir.path)
                    _uiState.update {
                        it.copy(
                            engineReady = true,
                            engineBusy = false,
                            statusMessage = "${model.displayName} is ready. New inbox files will be sorted by meaning.",
                            errorMessage = null,
                        )
                    }
                } catch (error: Exception) {
                    _uiState.update {
                        it.copy(engineReady = false, engineBusy = false)
                    }
                    recordFailure("Could not load ${model.displayName}", error)
                }
            }
        }
    }

    suspend fun processInboxOnce(): Int {
        val folderUri = _uiState.value.watchFolderUri ?: return 0
        return try {
            val tree = Uri.parse(folderUri)
            val root = withContext(Dispatchers.IO) { scanner.root(tree) }
                ?: run {
                    recordFailure("Could not open the inbox folder. Pick it again.")
                    return 0
                }
            val files = withContext(Dispatchers.IO) {
                scanner.listInbox(tree).filter { file ->
                    val uri = file.uri.toString()
                    uri !in skippedUris && database.organizedFileDao().countByOriginalUri(uri) == 0
                }
            }
            var processed = 0
            for (file in files) {
                processDocument(root, file)
                processed += 1
            }
            processed
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            recordFailure("Could not scan the inbox folder", error)
            0
        }
    }

    private suspend fun processDocument(
        root: androidx.documentfile.provider.DocumentFile,
        file: androidx.documentfile.provider.DocumentFile,
    ) {
        val fileUri = file.uri.toString()
        if (fileUri in skippedUris) return
        if (database.organizedFileDao().countByOriginalUri(fileUri) > 0) return

        val fileName = file.name ?: "unnamed file"
        _uiState.update { it.copy(processingName = fileName, statusMessage = "Reading $fileName…") }
        var preview: com.smartorganizer.agent.files.FilePreview? = null
        try {
            preview = withContext(Dispatchers.IO) { previewer.prepare(file) }
            val currentPreview = preview
            val model = _uiState.value.selectedModel
            val classification = if (
                !model.supportsVision && currentPreview.extractedText.trim().length < MIN_OCR_CHARS
            ) {
                NoTextFallback.classify(currentPreview.displayName, currentPreview.mimeType)
            } else {
                withContext(llmDispatcher) {
                    llmMutex.withLock {
                        val path = downloader.modelFile(model).absolutePath
                        if (!classifier.isLoaded(path)) {
                            classifier.load(model, path, app.cacheDir.path)
                            _uiState.update { it.copy(engineReady = true) }
                        }
                        _uiState.update {
                            it.copy(
                                engineBusy = true,
                                statusMessage = "${model.displayName} is classifying ${currentPreview.displayName}…",
                            )
                        }
                        classifier.classify(
                            fileName = currentPreview.displayName,
                            mimeType = currentPreview.mimeType,
                            imagePaths = currentPreview.imagePaths,
                            extractedText = currentPreview.extractedText,
                        )
                    }
                }
            }
            val destination = withContext(Dispatchers.IO) {
                organizer.moveToCategory(root, file, classification.category)
            }
            database.organizedFileDao().insert(
                OrganizedFile(
                    originalName = currentPreview.displayName,
                    originalUri = fileUri,
                    destinationUri = destination.uri.toString(),
                    destinationFolder = classification.category.folderName,
                    category = classification.category.name,
                    mimeType = currentPreview.mimeType,
                    confidence = classification.confidence,
                    reason = classification.reason,
                    organizedAt = System.currentTimeMillis(),
                ),
            )
            _uiState.update {
                it.copy(
                    engineBusy = false,
                    processingName = null,
                    statusMessage = "${currentPreview.displayName} → ${classification.category.folderName}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            skipFailedFile(fileUri, fileName, error)
        } finally {
            preview?.cleanup()
        }
    }

    private fun handleDownloadStatus(model: OrganizerModel, status: DownloadStatus) {
        when (status) {
            DownloadStatus.Idle -> Unit
            is DownloadStatus.Progress -> _uiState.update {
                it.copy(
                    downloading = true,
                    downloadBytes = status.bytes,
                    downloadTotal = status.total,
                    statusMessage = "Downloading ${model.displayName}… ${formatBytes(status.bytes)} / ${formatBytes(status.total)}",
                )
            }
            is DownloadStatus.Ready -> _uiState.update {
                it.copy(
                    downloading = false,
                    modelReady = true,
                    downloadBytes = status.path.let { path -> java.io.File(path).length() },
                    downloadTotal = model.expectedBytes,
                    statusMessage = "${model.displayName} is saved on this phone. You can use it offline now.",
                    errorMessage = null,
                )
            }
            is DownloadStatus.Failed -> {
                _uiState.update { it.copy(downloading = false) }
                recordFailure(status.message)
            }
        }
    }

    private fun formatBytes(value: Long): String {
        if (value < 1_000_000L) return "${value / 1024} KB"
        val mb = value / 1_000_000.0
        return if (mb >= 1000) String.format("%.2f GB", mb / 1000.0) else String.format("%.0f MB", mb)
    }

    companion object {
        private const val MIN_OCR_CHARS = 12
    }
}
