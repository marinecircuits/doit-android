package com.marinecircuits.doit.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.TextFieldValue
import com.marinecircuits.doit.data.Recurrence
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleInputTest {
    @get:Rule val ui = createComposeRule()

    private fun content(state: ScheduleState) {
        ui.setContent {
            MaterialTheme {
                var text by remember { mutableStateOf(TextFieldValue()) }
                state.changeText = { text = it }
                Column {
                    OutlinedTextField(text, onValueChange = { text = it }, modifier = Modifier.testTag("title")
                        .onPreviewKeyEvent { state.key(it, text) })
                    ScheduleSuggestions(state, text)
                    ScheduleChips(state)
                }
                ScheduleDialogs(state)
            }
        }
    }

    @Test
    fun typingKeepsFocusThenDateConfirmationCreatesARemovableChip() {
        val state = ScheduleState()
        content(state)
        ui.onNodeWithTag("title").performClick().performTextInput("Water !wee")
        ui.onNodeWithTag("title").assertIsFocused()
        ui.runOnIdle { assertNull(state.picker); assertFalse(state.rule.active) }
        ui.onNodeWithText("Repeats weekly").performClick()
        ui.onNodeWithText("OK").performClick()
        ui.onNodeWithText("Set repeat").performClick()
        ui.runOnIdle { assertEquals(Recurrence.Rule(Recurrence.WEEK), state.rule); assertNotNull(state.day) }
        ui.onNodeWithText("Water").assertExists()
        ui.onNodeWithText("Remove repeat").performClick()
        ui.runOnIdle { assertFalse(state.rule.active); assertNotNull(state.day) }
    }

    @Test
    fun cancellingATypoSuggestionLeavesOriginalTextAndScheduleUntouched() {
        val state = ScheduleState()
        content(state)
        ui.onNodeWithTag("title").performClick().performTextInput("Water !weelky")
        ui.onNodeWithText("Did you mean !weekly?").performClick()
        ui.onAllNodesWithText("Cancel")[1].performClick()
        ui.onNodeWithText("Cancel").performClick()
        ui.onNodeWithText("Water !weelky").assertExists()
        ui.runOnIdle { assertFalse(state.rule.active); assertNull(state.day) }
    }

    @Test
    fun editingExistingHourlyRepeatPreservesItsInterval() {
        val state = ScheduleState(java.time.LocalDate.of(2027, 1, 5), Recurrence.Rule(7200, 2))
        content(state)
        ui.onNodeWithText("Repeats every 2 hours after completion").performClick()
        ui.onNodeWithText("Set repeat").performClick()
        ui.runOnIdle { assertEquals(Recurrence.Rule(7200, 2), state.rule) }
    }
}
