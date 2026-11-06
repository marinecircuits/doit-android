@file:OptIn(ExperimentalMaterial3Api::class)

package com.marinecircuits.doit.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.R
import com.marinecircuits.doit.data.CaptureSyntax
import com.marinecircuits.doit.data.Recurrence
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.marinecircuits.doit.data.Shopping
import com.marinecircuits.doit.data.DueDates
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Material's date picker works in UTC midnights; this converts to and from local dates. */
@Composable
fun PickDateDialog(initial: LocalDate?, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** The light edit sheet: changes are saved as each field is committed. */
@Composable
fun EditSheet(
    task: TaskEntity,
    projects: List<ProjectEntity>,
    labels: List<LabelEntity>,
    shoppingProjects: Set<Long>,
    today: LocalDate,
    words: DueWords,
    locale: Locale,
    onTitleChange: (String) -> Unit,
    onScheduleChange: (Recurrence.Rule, LocalDate?) -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onProjectChange: (Long) -> Unit,
    onDelete: () -> Unit,
    working: Boolean,
    onWork: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var title by remember(task.id) { mutableStateOf(TextFieldValue(task.title, TextRange(task.title.length))) }
    var savedTitle by remember(task.id) { mutableStateOf(task.title) }
    var titleFocused by remember(task.id) { mutableStateOf(false) }
    val schedule = remember(task.id) { ScheduleState(task.dueAt?.let { DueDates.localDate(it, zone) }, Recurrence.Rule(task.repeatAfter, task.repeatMode)) }
    schedule.changeText = { title = it }
    schedule.onChange = { rule, day -> if (day == null && !rule.active) onDateChange(null) else onScheduleChange(rule, day) }
    var projectId by remember(task.id) { mutableStateOf(task.projectId) }
    var projectMenuOpen by remember { mutableStateOf(false) }
    val projectPickerItems = remember(projects) { ProjectTree.pickerItems(projects) }
    var confirmingDelete by remember(task.id) { mutableStateOf(false) }

    fun commitTitle(): Boolean {
        if (!schedule.prepare(title, preview = false)) return false
        val raw = title.text
        if (raw == savedTitle) return true
        if (raw.isBlank()) {
            title = TextFieldValue(savedTitle, TextRange(savedTitle.length))
            return true
        }
        val parsed = Shopping.parse(raw, labels, projects, projectId, shoppingProjects)
        val shown = parsed.title.ifEmpty { savedTitle }
        parsed.projectId?.let { projectId = it }
        savedTitle = shown
        title = TextFieldValue(shown, TextRange(shown.length))
        onTitleChange(raw)
        return true
    }

    fun dismiss() {
        if (commitTitle()) onDismiss()
    }

    ModalBottomSheet(onDismissRequest = ::dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden || commitTitle() })) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.edit_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { schedule.key(it, title) }.onFocusChanged { focus ->
                    if (titleFocused && !focus.isFocused) commitTitle()
                    titleFocused = focus.isFocused
                },
            )
            if (titleFocused) {
                SyntaxKeyRow(onInsert = { token -> title = SyntaxKeys.insert(title, token) }, onPickDate = { title = SyntaxKeys.insert(title, "!") })
                ScheduleSuggestions(schedule, title, Modifier.fillMaxWidth())
                val suggestions = if (title.selection.collapsed) {
                    CaptureSyntax.suggestionsAt(title.text, title.selection.end, labels, projects)
                } else {
                    emptyList()
                }
                if (suggestions.isNotEmpty()) {
                    CaptureSuggestions(
                        suggestions,
                        onPick = { suggestion ->
                            CaptureSyntax.complete(title.text, title.selection.end, suggestion.title)?.let {
                                title = TextFieldValue(it.text, TextRange(it.cursor))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ScheduleChips(schedule, Modifier.fillMaxWidth(), showEmptyRepeat = true)
            if (schedule.day == null) TextButton(onClick = { schedule.picker = "date" }) { Text("Set due date…") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.edit_project), Modifier.weight(1f))
                Box {
                    TextButton(onClick = { projectMenuOpen = true }) {
                        Text(projects.firstOrNull { it.id == projectId }?.title ?: "—")
                    }
                    DropdownMenu(expanded = projectMenuOpen, onDismissRequest = { projectMenuOpen = false }) {
                        projectPickerItems.forEach { item ->
                            val project = item.project
                            DropdownMenuItem(
                                text = { Text(project.title, Modifier.padding(start = (item.depth * 16).dp)) },
                                onClick = {
                                    if (projectId != project.id) {
                                        projectId = project.id
                                        onProjectChange(project.id)
                                    }
                                    projectMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
            if (onWork != null && projectId !in shoppingProjects) {
                TextButton(onClick = onWork) {
                    Text(stringResource(if (working) R.string.stop_working else R.string.start_working))
                }
            }
            TextButton(onClick = { confirmingDelete = true }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    ScheduleDialogs(schedule)
}
