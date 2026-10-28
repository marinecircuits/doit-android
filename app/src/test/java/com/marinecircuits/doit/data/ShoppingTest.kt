package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.remote.VikunjaJson
import com.marinecircuits.doit.ui.tasks.TaskListModel
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShoppingTest {
    @Test
    fun sharedMarkerFixturesMatchLinux() {
        val data = javaClass.getResource("/shopping-marker.json")!!.readText()
        for (item in VikunjaJson.parseToJsonElement(data).jsonArray) {
            val test = item.jsonObject
            val description = test.getValue("description").jsonPrimitive.content
            assertEquals(description, test.getValue("shopping").jsonPrimitive.boolean, Shopping.isProject(description))
        }
    }

    @Test
    fun quantitiesAreFreeTextAndOnlyFinalNonemptyBracketsCount() {
        for (amount in listOf("2 L", "one bag", "1,5 kg", "2 × 400 g", "a couple", "ask @doing #Work", "two  bags")) {
            assertEquals("Milk" to amount, Shopping.item("Milk [$amount]"))
        }
        for (title in listOf("Milk []", "Milk [ ]", "Milk [one", "[one bag]", "Milk [one] tomorrow", "Milk[2]")) {
            assertEquals(title to null, Shopping.item(title))
        }
    }

    @Test
    fun quantityTokensStayLiteralAndOnlyTheDirectProjectIsMarked() {
        val projects = listOf(ProjectEntity(1, "Groceries", false, 1.0, description = "<p>doit:type=shopping</p>"),
            ProjectEntity(2, "Work", false, 2.0), ProjectEntity(3, "Child", false, 3.0, parentProjectId = 1))
        assertEquals(setOf(1L), Shopping.projectIds(projects))
        val parsed = Shopping.parseCapture("Milk #Groceries @errand [ask @doing or #Work for two bags]",
            listOf(LabelEntity(7, "Errand"), LabelEntity(8, "Doing")), projects, 2)
        assertEquals("Milk [ask @doing or #Work for two bags]", parsed.title)
        assertEquals(listOf("Errand"), parsed.labels)
        assertEquals(1L, parsed.projectId)
    }

    @Test
    fun rowsKeepStoredTitleWhileShowingQuantityAndFilteringDoing() {
        val projects = listOf(ProjectEntity(1, "Groceries", false, 1.0, description = Shopping.MARKER),
            ProjectEntity(2, "Work", false, 2.0))
        val tasks = listOf(TaskEntity(1, "Milk [one carton]", null, 1, false, 0),
            TaskEntity(2, "Draft [one copy]", null, 2, false, 0))
        val rows = TaskListModel.rows(tasks, projects, null, LocalDate.of(2026, 10, 28), ZoneOffset.UTC,
            taskLabels = mapOf(1L to listOf("Doing", "Errand")))
        assertTrue(rows[0].shopping)
        assertEquals("Milk", rows[0].displayTitle)
        assertEquals("one carton", rows[0].quantity)
        assertEquals("Milk [one carton]", rows[0].task.title)
        assertEquals(listOf("Errand"), rows[0].labels.map { it.title })
        assertFalse(rows[1].shopping)
        assertEquals("Draft [one copy]", rows[1].displayTitle)
        assertEquals(null, rows[1].quantity)
    }
}
