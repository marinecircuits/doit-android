package com.marinecircuits.doit.ui.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.marinecircuits.doit.data.local.TaskEntity
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DueDateChipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dateTapDoesNotOpenTheTaskEditor() {
        var dates = 0
        var edits = 0
        val task = TaskEntity(1, "Example", null, 1, false, 0)
        compose.setContent { MaterialTheme {
            TaskRowItem(TaskRow(task, LocalDate.of(2026, 11, 13), false, null), "Today",
                onCheckedChange = {}, onOpen = { edits++ }, onDateClick = { dates++ }, working = false, onWork = null)
        } }
        compose.onNodeWithText("Today").performClick()
        assertEquals(1, dates)
        assertEquals(0, edits)
    }
}
