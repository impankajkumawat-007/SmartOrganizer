package com.smartorganizer.agent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "organized_files")
data class OrganizedFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalName: String,
    val originalUri: String,
    val destinationUri: String,
    val destinationFolder: String,
    val category: String,
    val mimeType: String,
    val confidence: Float,
    val reason: String,
    val organizedAt: Long,
)
