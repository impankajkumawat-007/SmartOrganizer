package com.smartorganizer.agent.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("smart_organizer_prefs")

class UserPreferences(private val context: Context) {
    val watchFolderUri: Flow<String?> = context.dataStore.data.map { it[WATCH_FOLDER_URI] }
    val huggingFaceToken: Flow<String> = context.dataStore.data.map { it[HF_TOKEN].orEmpty() }

    suspend fun setWatchFolderUri(uri: String) {
        context.dataStore.edit { it[WATCH_FOLDER_URI] = uri }
    }

    suspend fun setHuggingFaceToken(token: String) {
        context.dataStore.edit { it[HF_TOKEN] = token.trim() }
    }

    private companion object {
        val WATCH_FOLDER_URI = stringPreferencesKey("watch_folder_uri")
        val HF_TOKEN = stringPreferencesKey("hf_token")
    }
}
