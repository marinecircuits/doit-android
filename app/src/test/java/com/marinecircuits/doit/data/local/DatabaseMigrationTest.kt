package com.marinecircuits.doit.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {
    @Test
    fun existingProjectsSurviveTheParentIdMigration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-1-2.db"
        context.deleteDatabase(name)
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                old.execSQL("CREATE TABLE tasks (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, dueAt INTEGER, projectId INTEGER NOT NULL, done INTEGER NOT NULL, createdAt INTEGER NOT NULL, syncGeneration INTEGER NOT NULL)")
                old.execSQL("CREATE INDEX index_tasks_projectId ON tasks (projectId)")
                old.execSQL("CREATE TABLE projects (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, archived INTEGER NOT NULL, position REAL NOT NULL)")
                old.execSQL("CREATE TABLE pending_changes (seq INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, kind TEXT NOT NULL, fields TEXT NOT NULL, revision INTEGER NOT NULL)")
                old.execSQL("CREATE UNIQUE INDEX index_pending_changes_taskId ON pending_changes (taskId)")
                old.execSQL("INSERT INTO projects VALUES (7, 'Existing', 0, 1.0)")
                old.version = 1
            }

            val upgraded = Room.databaseBuilder(context, DoitDatabase::class.java, name)
                .addMigrations(DoitDatabase.MIGRATION_1_2)
                .build()
            try {
                assertEquals(listOf(ProjectEntity(7, "Existing", false, 1.0, 0)), runBlocking { upgraded.projects().all() })
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
