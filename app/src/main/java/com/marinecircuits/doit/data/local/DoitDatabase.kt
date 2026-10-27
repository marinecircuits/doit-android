package com.marinecircuits.doit.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TaskEntity::class,
        ProjectEntity::class,
        PendingChangeEntity::class,
        LabelEntity::class,
        TaskLabelEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // 2: projects.parentProjectId, 3: projects.hexColor, 4: labels and task_labels
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class DoitDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun projects(): ProjectDao
    abstract fun pendingChanges(): PendingChangeDao
    abstract fun labels(): LabelDao
    abstract fun taskLabels(): TaskLabelDao

    companion object {
        fun build(context: Context): DoitDatabase =
            Room.databaseBuilder(context, DoitDatabase::class.java, "doit.db").build()
    }
}
