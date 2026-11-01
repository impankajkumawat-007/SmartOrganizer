package com.smartorganizer.agent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "failed_files")
data class FailedFile(
    @PrimaryKey val uri: String,
    val fileName: String,
    val error: String,
    val failedAt: Long,
)
