package com.smartorganizer.agent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FailedFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: FailedFile)

    @Query("SELECT uri FROM failed_files")
    suspend fun uris(): List<String>
}
