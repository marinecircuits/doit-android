package com.marinecircuits.doit.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, ProjectEntity::class, PendingChangeEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class DoitDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun projects(): ProjectDao
    abstract fun pendingChanges(): PendingChangeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN parentProjectId INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): DoitDatabase =
            Room.databaseBuilder(context, DoitDatabase::class.java, "doit.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
