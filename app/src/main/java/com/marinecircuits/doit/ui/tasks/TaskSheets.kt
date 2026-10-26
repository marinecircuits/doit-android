@file:OptIn(ExperimentalMaterial3Api::class)

package com.marinecircuits.doit.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.R
import com.marinecircuits.doit.data.DueDates
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Opened by a left swipe: the fixed choices apply at once. */
@Composable
fun RescheduleSheet(
    hasDate: Boolean,
    today: LocalDate,
    onPick: (LocalDate?) -> Unit,
    onPickDate: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.when_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        QuickDate.entries.forEach { choice ->
            val label = when (choice) {
                QuickDate.TODAY -> R.string.when_today
                QuickDate.TOMORROW -> R.string.when_tomorrow
                QuickDate.NEXT_WEEK -> R.string.when_next_week
            }
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                modifier = Modifier.clickable { onPick(QuickDates.dateFor(choice, today)) },
            )
        }
        ListItem(headlineContent = { Text(stringResource(R.string.when_pick)) }, modifier = Modifier.clickable(onClick = onPickDate))
        if (hasDate) {
            ListItem(headlineContent = { Text(stringResource(R.string.when_none)) }, modifier = Modifier.clickable { onPick(null) })
        }
    }
}

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
    today: LocalDate,
    words: DueWords,
    locale: Locale,
    onTitleChange: (String) -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onProjectChange: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var title by remember(task.id) { mutableStateOf(task.title) }
    var lastSavedTitle by remember(task.id) { mutableStateOf(task.title) }
    var titleFocused by remember(task.id) { mutableStateOf(false) }
    var date by remember(task.id) { mutableStateOf(task.dueAt?.let { DueDates.localDate(it, zone) }) }
    var projectId by remember(task.id) { mutableStateOf(task.projectId) }
    var pickingDate by remember { mutableStateOf(false) }
    var projectMenuOpen by remember { mutableStateOf(false) }

    fun commitTitle() {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty() && trimmed != lastSavedTitle) {
            lastSavedTitle = trimmed
            onTitleChange(trimmed)
        }
    }

    fun dismiss() {
        commitTitle()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = ::dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
                modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                    if (titleFocused && !focus.isFocused) commitTitle()
                    titleFocused = focus.isFocused
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.edit_due), Modifier.weight(1f))
                TextButton(onClick = { pickingDate = true }) { Text(dateLabel(date, today, words, locale)) }
                if (date != null) TextButton(onClick = {
                    date = null
                    onDateChange(null)
                }) { Text(stringResource(R.string.edit_clear_date)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.edit_project), Modifier.weight(1f))
                Box {
                    TextButton(onClick = { projectMenuOpen = true }) {
                        Text(projects.firstOrNull { it.id == projectId }?.title ?: "—")
                    }
                    DropdownMenu(expanded = projectMenuOpen, onDismissRequest = { projectMenuOpen = false }) {
                        projects.forEach { project ->
                            DropdownMenuItem(
                                text = { Text(project.title) },
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = ::dismiss) { Text(stringResource(R.string.close)) }
            }
        }
    }

    if (pickingDate) {
        PickDateDialog(
            initial = date ?: today,
            onPicked = {
                if (date != it) {
                    date = it
                    onDateChange(it)
                }
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}
