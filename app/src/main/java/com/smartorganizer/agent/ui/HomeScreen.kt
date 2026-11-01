package com.smartorganizer.agent.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartorganizer.agent.data.OrganizedFile
import com.smartorganizer.agent.runtime.OrganizerUiState
import com.smartorganizer.agent.ui.theme.Danger
import com.smartorganizer.agent.ui.theme.Gold
import com.smartorganizer.agent.ui.theme.Ink
import com.smartorganizer.agent.ui.theme.Moss
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeRoute(viewModel: OrganizerViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setWatchFolder(uri)
        }
    }

    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importModel(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshModel()
        if (Build.VERSION.SDK_INT >= 33) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    HomeScreen(
        state = state,
        onTokenChange = viewModel::setHuggingFaceToken,
        onDownload = viewModel::startDownload,
        onCancelDownload = viewModel::cancelDownload,
        onImportModel = { modelPicker.launch(arrayOf("*/*")) },
        onPickFolder = {
            val initial = state.watchFolderUri?.let { Uri.parse(it) }
            folderPicker.launch(initial)
        },
        onToggleWatch = {
            if (state.watching) viewModel.stopWatching() else viewModel.startWatching()
        },
    )
}

@Composable
private fun HomeScreen(
    state: OrganizerUiState,
    onTokenChange: (String) -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onImportModel: () -> Unit,
    onPickFolder: () -> Unit,
    onToggleWatch: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("Smart Organizer", style = MaterialTheme.typography.headlineLarge, color = Ink)
                Text(
                    "Drop files into one folder. Gemma 4 reads what they mean and files them.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FailureBanner(state)
            ModelCard(state, onTokenChange, onDownload, onCancelDownload, onImportModel)
            FolderCard(state, onPickFolder, onToggleWatch)
            HistoryCard(state.recentFiles)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelCard(
    state: OrganizerUiState,
    onTokenChange: (String) -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onImportModel: () -> Unit,
) {
    SectionCard(icon = Icons.Outlined.Memory, title = "Gemma 4 E2B") {
        Text(
            if (state.modelReady) "Saved on this phone. Inference stays offline."
            else "One-time 2.6 GB download from Hugging Face. Not packed inside the app.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.modelReady) {
            StatusRow("Model ready", Moss)
        }
        if (state.engineReady) {
            StatusRow("Loaded in memory", Moss)
        }
        Text(state.statusMessage, style = MaterialTheme.typography.bodyMedium)
        if (state.downloading) {
            LinearProgressIndicator(
                progress = { state.downloadFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Gold,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        if (!state.modelReady) {
            OutlinedTextField(
                value = state.huggingFaceToken,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hugging Face token (if the model is gated)") },
                singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.modelReady && !state.downloading) {
                Button(onClick = onDownload) { Text("Download once") }
                OutlinedButton(onClick = onImportModel) { Text("Import file") }
            }
            if (state.downloading) {
                OutlinedButton(onClick = onCancelDownload) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun FolderCard(
    state: OrganizerUiState,
    onPickFolder: () -> Unit,
    onToggleWatch: () -> Unit,
) {
    SectionCard(icon = Icons.Outlined.FolderOpen, title = "Inbox folder") {
        Text(
            "Pick the parent folder to watch. Resume, Ticket, Bill and other folders are created inside it.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            state.watchFolderName ?: "No folder selected",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (state.processingName != null) {
            Text("Sorting ${state.processingName}", color = Moss, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickFolder) { Text("Choose folder") }
            Button(
                onClick = onToggleWatch,
                enabled = state.watching || (state.modelReady && !state.watchFolderUri.isNullOrBlank()),
            ) {
                Icon(
                    imageVector = if (state.watching) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(if (state.watching) "Stop watching" else "Start watching")
            }
        }
    }
}

@Composable
private fun FailureBanner(state: OrganizerUiState) {
    val message = state.errorMessage ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFECEC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Danger)
                Text("Error", style = MaterialTheme.typography.titleLarge, color = Danger)
            }
            Text(
                text = message,
                color = Danger,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HistoryCard(files: List<OrganizedFile>) {
    SectionCard(icon = Icons.Rounded.CheckCircle, title = "Recently sorted") {
        if (files.isEmpty()) {
            Text("Nothing sorted yet. Add a PDF or photo to the inbox after watching starts.")
        } else {
            files.take(20).forEach { file ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(file.originalName, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${file.destinationFolder}  ·  ${(file.confidence * 100).toInt()}%  ·  ${
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(file.organizedAt))
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (file.reason.isNotBlank()) {
                        Text(file.reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = Gold)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

@Composable
private fun StatusRow(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = color, fontWeight = FontWeight.Medium)
    }
}
