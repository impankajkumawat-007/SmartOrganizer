package com.smartorganizer.agent.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.smartorganizer.agent.SmartOrganizerApp
import com.smartorganizer.agent.llm.OrganizerModel
import com.smartorganizer.agent.runtime.OrganizerRuntime

class OrganizerViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime: OrganizerRuntime = (application as SmartOrganizerApp).runtime
    val uiState = runtime.uiState

    fun setSelectedModel(model: OrganizerModel) = runtime.setSelectedModel(model)
    fun setHuggingFaceToken(token: String) = runtime.setHuggingFaceToken(token)
    fun setWatchFolder(uri: Uri) = runtime.setWatchFolder(uri)
    fun startDownload() = runtime.startDownload()
    fun importModel(uri: Uri) = runtime.importModel(uri)
    fun cancelDownload() = runtime.cancelDownload()
    fun startWatching() = runtime.startWatching()
    fun stopWatching() = runtime.stopWatching()
    fun refreshModel() = runtime.refreshModelStatus()
}
