package com.smartorganizer.agent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OrganizedFile::class, FailedFile::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun organizedFileDao(): OrganizedFileDao
    abstract fun failedFileDao(): FailedFileDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "smart-organizer.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
