package com.marinecircuits.doit.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, ProjectEntity::class, PendingChangeEntity::class],
    version = 3,
    exportSchema = true,
    // 2: projects.parentProjectId, 3: projects.hexColor
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class DoitDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun projects(): ProjectDao
    abstract fun pendingChanges(): PendingChangeDao

    companion object {
        fun build(context: Context): DoitDatabase =
            Room.databaseBuilder(context, DoitDatabase::class.java, "doit.db").build()
    }
}
