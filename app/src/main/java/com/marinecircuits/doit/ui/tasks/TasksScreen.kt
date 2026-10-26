@file:OptIn(ExperimentalMaterial3Api::class)

package com.marinecircuits.doit.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marinecircuits.doit.R
import com.marinecircuits.doit.data.local.TaskEntity
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The single screen: capture box, status line, the list of open tasks and the sheets for rescheduling and editing.
 * [focusCaptureRequests] grows each time the widget's + button asks for the capture box.
 */
@Composable
fun TasksScreen(viewModel: TasksViewModel, focusCaptureRequests: Int, onEnterNewToken: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val words = DueWords(
        today = stringResource(R.string.due_today),
        tomorrow = stringResource(R.string.due_tomorrow),
        yesterday = stringResource(R.string.due_yesterday),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val captureFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    var captureText by remember { mutableStateOf("") }
    var scrollToTaskId by remember { mutableStateOf<Long?>(null) }
    var rescheduleTarget by remember { mutableStateOf<TaskEntity?>(null) }
    var datePickTarget by remember { mutableStateOf<TaskEntity?>(null) }
    var editTarget by remember { mutableStateOf<TaskEntity?>(null) }

    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { }
    }

    LaunchedEffect(focusCaptureRequests) {
        if (focusCaptureRequests > 0) {
            captureFocus.requestFocus()
            keyboard?.show()
        }
    }

    LaunchedEffect(state.rows, scrollToTaskId) {
        val index = state.rows.indexOfFirst { it.task.id == scrollToTaskId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            scrollToTaskId = null
        }
    }

    LaunchedEffect(state.notices) {
        val notices = state.notices
        if (notices.isEmpty()) return@LaunchedEffect
        viewModel.clearNotices()
        notices.forEach { snackbarHostState.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long) }
    }

    fun complete(task: TaskEntity) {
        viewModel.complete(task)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.completed_snackbar, task.title),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoComplete(task)
        }
    }

    fun capture() {
        val text = captureText
        if (text.isBlank()) return
        captureText = ""
        viewModel.capture(text) { id -> scrollToTaskId = id }
    }

    // The capture bar handles the status bar itself; the list stays above the keyboard and navigation bar.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding().imePadding()) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            CaptureBar(
                text = captureText,
                onTextChange = { captureText = it },
                onSend = ::capture,
                onSyncNow = viewModel::refresh,
                onSignOut = viewModel::requestSignOut,
                focusRequester = captureFocus,
            )
            if (state.tokenRejected) TokenRejectedBanner(onEnterNewToken)
            StatusLine(state.status)

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.rows.isNotEmpty() -> LazyColumn(
                        state = listState,
                        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.rows, key = { it.task.id }) { row ->
                            SwipeRow(
                                onSwipedRight = { complete(row.task) },
                                onSwipedLeft = { rescheduleTarget = row.task },
                                modifier = Modifier.animateItem(),
                            ) {
                                TaskRowItem(
                                    row = row,
                                    dueLabel = row.dueDate?.let { DueLabels.label(it, state.today, words, locale) },
                                    onComplete = { complete(row.task) },
                                    onOpen = { editTarget = row.task },
                                )
                            }
                        }
                    }
                    // An empty database before the first sync is "loading", not "nothing to do".
                    !state.loaded || !state.syncedOnce -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 96.dp), Alignment.Center) {
                                Text(stringResource(R.string.empty_list), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    rescheduleTarget?.let { task ->
        RescheduleSheet(
            hasDate = task.dueAt != null,
            today = state.today,
            onPick = { date ->
                rescheduleTarget = null
                viewModel.reschedule(task, date)
            },
            onPickDate = {
                rescheduleTarget = null
                datePickTarget = task
            },
            onDismiss = { rescheduleTarget = null },
        )
    }

    datePickTarget?.let { task ->
        PickDateDialog(
            initial = state.rows.firstOrNull { it.task.id == task.id }?.dueDate ?: state.today,
            onPicked = { date ->
                datePickTarget = null
                viewModel.reschedule(task, date)
            },
            onDismiss = { datePickTarget = null },
        )
    }

    editTarget?.let { task ->
        EditSheet(
            task = task,
            projects = state.projects,
            today = state.today,
            words = words,
            locale = locale,
            onSave = { title, date, projectId ->
                editTarget = null
                viewModel.edit(task, title, date, projectId)
            },
            onDismiss = { editTarget = null },
        )
    }

    state.signOutConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSignOut,
            title = { Text(stringResource(R.string.sign_out_title)) },
            text = { Text(pluralStringResource(R.plurals.sign_out_pending, pending, pending)) },
            confirmButton = { TextButton(onClick = viewModel::confirmSignOut) { Text(stringResource(R.string.menu_sign_out)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissSignOut) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun CaptureBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    focusRequester: FocusRequester,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.capture_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            trailingIcon = {
                IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                    Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.capture_send))
                }
            },
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_sync)) },
                    onClick = {
                        menuOpen = false
                        onSyncNow()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_sign_out)) },
                    onClick = {
                        menuOpen = false
                        onSignOut()
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusLine(parts: List<StatusPart>) {
    if (parts.isEmpty()) return
    val texts = parts.map { part ->
        when (part) {
            StatusPart.Offline -> stringResource(R.string.status_offline)
            StatusPart.Failed -> stringResource(R.string.status_failed)
            StatusPart.MissingPermission -> stringResource(R.string.status_missing_permission)
            is StatusPart.Waiting -> pluralStringResource(R.plurals.status_waiting, part.count, part.count)
        }
    }
    Text(
        texts.joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun TokenRejectedBanner(onEnterNewToken: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.token_rejected_banner), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onEnterNewToken) { Text(stringResource(R.string.token_rejected_action)) }
        }
    }
}

@Composable
private fun TaskRowItem(row: TaskRow, dueLabel: String?, onComplete: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clickable(onClickLabel = stringResource(R.string.complete_task), role = Role.Checkbox, onClick = onComplete),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(22.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(row.task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                row.projectTitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            dueLabel?.let {
                Spacer(Modifier.width(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (row.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun dateLabel(date: LocalDate?, today: LocalDate, words: DueWords, locale: Locale): String =
    date?.let { DueLabels.label(it, today, words, locale) } ?: stringResource(R.string.edit_no_date)
