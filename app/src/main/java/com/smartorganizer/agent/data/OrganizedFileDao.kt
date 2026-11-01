package com.smartorganizer.agent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OrganizedFileDao {
    @Query("SELECT * FROM organized_files ORDER BY organizedAt DESC")
    fun observeAll(): Flow<List<OrganizedFile>>

    @Insert
    suspend fun insert(file: OrganizedFile): Long

    @Query("SELECT COUNT(*) FROM organized_files WHERE originalUri = :uri")
    suspend fun countByOriginalUri(uri: String): Int
}
