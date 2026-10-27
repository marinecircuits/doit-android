package com.marinecircuits.doit.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marinecircuits.doit.data.remote.VikunjaJson
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    /** Creates the database exactly as the given version of the app did, from the exported schema. */
    private fun createVersion(version: Int, fill: SQLiteDatabase.() -> Unit) {
        val schema = VikunjaJson.parseToJsonElement(
            File("schemas/com.marinecircuits.doit.data.local.DoitDatabase/$version.json").readText(),
        ).jsonObject.getValue("database").jsonObject
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.getValue("entities").jsonArray.forEach { element ->
                val entity = element.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                fun create(sql: JsonElement) = db.execSQL(sql.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                create(entity.getValue("createSql"))
                entity["indices"]?.jsonArray?.forEach { create(it.jsonObject.getValue("createSql")) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.fill()
            db.version = version
        }
    }

    private fun upgrade(check: suspend (DoitDatabase) -> Unit) {
        val upgraded = Room.databaseBuilder(context, DoitDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            runBlocking { check(upgraded) }
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun version1DataSurvivesTheUpgrade() {
        createVersion(1) {
            execSQL("INSERT INTO projects (id, title, archived, position) VALUES (7, 'Work', 0, 1.0)")
            execSQL(
                "INSERT INTO tasks (id, title, dueAt, projectId, done, createdAt, syncGeneration) " +
                    "VALUES (-1, 'Captured offline', NULL, 7, 0, 5, 0)",
            )
            execSQL(
                "INSERT INTO pending_changes (taskId, kind, fields, revision) " +
                    "VALUES (-1, 'create', '{\"title\":\"Captured offline\"}', 0)",
            )
        }

        upgrade { upgraded ->
            assertEquals(
                listOf(ProjectEntity(7, "Work", false, 1.0, parentProjectId = 0, hexColor = "")),
                upgraded.projects().all(),
            )
            assertEquals("Captured offline", upgraded.tasks().get(-1)?.title)
            assertEquals(PendingChangeEntity.CREATE, upgraded.pendingChanges().forTask(-1)?.kind)
        }
    }

    @Test
    fun version2DataSurvivesTheUpgrade() {
        createVersion(2) {
            execSQL(
                "INSERT INTO projects (id, title, archived, position, parentProjectId) VALUES (8, 'Firmware', 0, 2.0, 7)",
            )
            execSQL(
                "INSERT INTO tasks (id, title, dueAt, projectId, done, createdAt, syncGeneration) " +
                    "VALUES (12, 'Order capacitors', NULL, 8, 1, 5, 3)",
            )
            execSQL(
                "INSERT INTO pending_changes (taskId, kind, fields, revision) " +
                    "VALUES (12, 'update', '{\"done\":true}', 2)",
            )
        }

        upgrade { upgraded ->
            assertEquals(
                listOf(ProjectEntity(8, "Firmware", false, 2.0, parentProjectId = 7, hexColor = "")),
                upgraded.projects().all(),
            )
            assertEquals(true, upgraded.tasks().get(12)?.done)
            assertEquals(2, upgraded.pendingChanges().forTask(12)?.revision)
        }
    }

    @Test
    fun version3DataSurvivesTheUpgrade() {
        createVersion(3) {
            execSQL(
                "INSERT INTO projects (id, title, archived, position, parentProjectId, hexColor) " +
                    "VALUES (8, 'Firmware', 0, 2.0, 7, '1973ff')",
            )
            execSQL(
                "INSERT INTO tasks (id, title, dueAt, projectId, done, createdAt, syncGeneration) " +
                    "VALUES (-2, 'Order capacitors', NULL, 8, 0, 5, 0)",
            )
            execSQL(
                "INSERT INTO pending_changes (taskId, kind, fields, revision) " +
                    "VALUES (-2, 'create', '{\"title\":\"Order capacitors\"}', 0)",
            )
        }

        upgrade { upgraded ->
            assertEquals("1973ff", upgraded.projects().all().single().hexColor)
            assertEquals("Order capacitors", upgraded.tasks().get(-2)?.title)
            assertEquals(PendingChangeEntity.CREATE, upgraded.pendingChanges().forTask(-2)?.kind)
            assertEquals(emptyList<LabelEntity>(), upgraded.labels().all())
            assertEquals(emptyList<String>(), upgraded.taskLabels().forTask(-2))
        }
    }
}
