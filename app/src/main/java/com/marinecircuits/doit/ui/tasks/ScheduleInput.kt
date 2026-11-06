package com.marinecircuits.doit.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.data.Recurrence
import java.time.LocalDate

/** The draft is only committed after a supported shortcut or a picker choice is accepted. */
@Stable
class ScheduleState(initialDay: LocalDate? = null, initialRule: Recurrence.Rule = Recurrence.Rule()) {
    var day by mutableStateOf(initialDay)
    var rule by mutableStateOf(initialRule)
    var picker by mutableStateOf<String?>(null)
    var proposedRule by mutableStateOf(initialRule)
    var pendingText: String? = null
    var changeText: (TextFieldValue) -> Unit = {}
    var onChange: (Recurrence.Rule, LocalDate?) -> Unit = { _, _ -> }
    var index by mutableIntStateOf(0)
    private var dismissedText by mutableStateOf<String?>(null)

    fun suggestions(value: TextFieldValue): List<Recurrence.Suggestion> {
        if (!value.selection.collapsed || value.text == dismissedText || value.text.trimStart().startsWith('/')) return emptyList()
        return Recurrence.typing(value.text, value.selection.end)?.let { Recurrence.suggestions(it.word) }.orEmpty()
    }

    fun accept(token: String, value: TextFieldValue) {
        val typing = Recurrence.typing(value.text, value.selection.end) ?: return
        val end = value.selection.end + value.text.drop(value.selection.end).takeWhile { !it.isWhitespace() }.length
        if (token == "date") {
            pendingText = value.text.removeRange(typing.start, end)
            picker = "date"
        } else {
            val parsed = Recurrence.parse(value.text.replaceRange(typing.start, end, "!$token"))
            val chosen = parsed.rule ?: return
            if (day == null) openRepeat(chosen, parsed.text) else {
                chooseRule(chosen)
                changeText(TextFieldValue(parsed.text, TextRange(parsed.text.length)))
            }
        }
        index = 0
    }

    fun prepare(value: TextFieldValue, preview: Boolean = true): Boolean {
        if (picker != null) return false
        val parsed = Recurrence.parse(value.text)
        val chosen = parsed.rule ?: return true
        if (day == null) { openRepeat(chosen, parsed.text); return false }
        chooseRule(chosen)
        changeText(TextFieldValue(parsed.text, TextRange(parsed.text.length)))
        return !preview
    }

    fun key(event: KeyEvent, value: TextFieldValue): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val matches = suggestions(value)
        if (matches.isEmpty()) return false
        when (event.key) {
            Key.DirectionDown -> index = (index + 1) % matches.size
            Key.DirectionUp -> index = (index + matches.size - 1) % matches.size
            Key.Tab, Key.Enter, Key.NumPadEnter -> accept(matches[index % matches.size].token, value)
            Key.Escape -> dismissedText = value.text
            else -> return false
        }
        return true
    }

    fun openRepeat(chosen: Recurrence.Rule = if (rule.active) rule else Recurrence.Rule(Recurrence.WEEK), text: String? = null) {
        proposedRule = chosen
        pendingText = text
        picker = "repeat"
    }

    fun chooseRule(chosen: Recurrence.Rule) { rule = chosen; onChange(rule, day) }
    fun chooseDay(chosen: LocalDate?) { day = chosen; onChange(rule, day) }
    fun cancel() { picker = null; pendingText = null }
    fun finish(chosen: Recurrence.Rule, date: LocalDate) {
        rule = chosen
        day = date
        onChange(chosen, date)
        pendingText?.let { changeText(TextFieldValue(it, TextRange(it.length))) }
        cancel()
    }
    fun reset() { rule = Recurrence.Rule(); day = null; cancel() }
}

@Composable
fun ScheduleSuggestions(state: ScheduleState, value: TextFieldValue, modifier: Modifier = Modifier) {
    val matches = state.suggestions(value)
    if (matches.isEmpty()) return
    Surface(modifier, shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp, shadowElevation = 4.dp) {
        Column {
            matches.forEachIndexed { index, suggestion ->
                Text(suggestion.title, Modifier.fillMaxWidth()
                    .background(if (index == state.index % matches.size) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { state.accept(suggestion.token, value) }.padding(12.dp))
            }
        }
    }
}

@Composable
fun ScheduleChips(state: ScheduleState, modifier: Modifier = Modifier, showEmptyRepeat: Boolean = false) {
    Column(modifier) {
        state.day?.let { date ->
            Row {
                TextButton(onClick = { state.picker = "date" }) { Text("Due $date") }
                if (!state.rule.active) TextButton(onClick = { state.chooseDay(null) }) { Text("Remove date") }
            }
        }
        if (state.rule.active || showEmptyRepeat) Row {
            TextButton(onClick = { state.openRepeat() }, modifier = Modifier.weight(1f, fill = false)) {
                Text(if (state.rule.active) state.rule.summary else "Set repeat…")
            }
            if (state.rule.active) TextButton(onClick = { state.chooseRule(Recurrence.Rule()) }) { Text("Remove repeat") }
        }
    }
}

@Composable
fun ScheduleDialogs(state: ScheduleState) {
    if (state.picker == "date") PickDateDialog(
        initial = state.day ?: LocalDate.now(),
        onPicked = { state.finish(state.rule, it) },
        onDismiss = state::cancel,
    )
    if (state.picker == "repeat") RepeatDialog(
        initial = state.proposedRule, firstDate = state.day,
        onSave = state::finish, onDismiss = state::cancel,
    )
}

@Composable
private fun RepeatDialog(initial: Recurrence.Rule, firstDate: LocalDate?, onSave: (Recurrence.Rule, LocalDate) -> Unit, onDismiss: () -> Unit) {
    var frequency by remember { mutableIntStateOf(when {
        initial.mode == Recurrence.MONTHLY -> 2
        initial.after == Recurrence.DAY -> 0
        initial.after == Recurrence.WEEK -> 1
        else -> 3
    }) }
    val units = listOf(Recurrence.DAY, Recurrence.WEEK, 3600L, 1L)
    val initialUnit = listOf(1, 0, 2, 3).firstOrNull { initial.after > 0 && initial.after % units[it] == 0L } ?: 0
    var unit by remember { mutableIntStateOf(initialUnit) }
    val initialAmount = (initial.after / units[initialUnit]).coerceAtLeast(1)
    var amount by remember { mutableStateOf(initialAmount.toString()) }
    var afterCompletion by remember { mutableStateOf(initial.mode == Recurrence.AFTER_COMPLETION) }
    var date by remember { mutableStateOf(firstDate) }
    var pickDate by remember { mutableStateOf(firstDate == null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat task") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Daily", "Weekly", "Monthly", "Custom interval").forEachIndexed { index, label ->
                    FilterChip(selected = frequency == index, onClick = { frequency = index; if (index == 2) afterCompletion = false }, label = { Text(label) })
                }
                if (frequency == 3) {
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Every") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = unit == 0, onClick = { unit = 0 }, label = { Text("Days") })
                        FilterChip(selected = unit == 1, onClick = { unit = 1 }, label = { Text("Weeks") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = unit == 2, onClick = { unit = 2 }, label = { Text("Hours") })
                        FilterChip(selected = unit == 3, onClick = { unit = 3 }, label = { Text("Seconds") })
                    }
                }
                Text("Count repeats from")
                FilterChip(selected = !afterCompletion, onClick = { afterCompletion = false }, label = { Text("Scheduled date") })
                if (frequency != 2) FilterChip(selected = afterCompletion, onClick = { afterCompletion = true }, label = { Text("Completion") })
                TextButton(onClick = { pickDate = true }) { Text(date?.let { "Next due date: $it" } ?: "Choose first due date…") }
            }
        },
        confirmButton = {
            TextButton(enabled = date != null && (frequency != 3 || amount.toLongOrNull() in 1..maxOf(9999, initialAmount)), onClick = {
                val after = when (frequency) { 0 -> Recurrence.DAY; 1 -> Recurrence.WEEK; 2 -> 0L; else -> amount.toLong() * units[unit] }
                val mode = if (frequency == 2) Recurrence.MONTHLY else if (afterCompletion) Recurrence.AFTER_COMPLETION else 0
                onSave(Recurrence.Rule(after, mode), requireNotNull(date))
            }) { Text("Set repeat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickDate) PickDateDialog(initial = date ?: LocalDate.now(), onPicked = { date = it; pickDate = false }, onDismiss = { pickDate = false })
}
