@file:OptIn(ExperimentalMaterial3Api::class)

package com.marinecircuits.doit.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marinecircuits.doit.R
import com.marinecircuits.doit.data.local.TaskEntity
import java.time.LocalDate
import java.util.Locale

/**
 * The main view and every project screen: capture box, status line, project tiles, and a task list in projects,
 * and the sheets for rescheduling and editing. [focusCaptureRequests] grows each time the widget's + button asks
 * for the capture box, which always opens the main view.
 */
@Composable
fun TasksScreen(viewModel: TasksViewModel, focusCaptureRequests: Int, onEnterNewToken: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val words = DueWords(
        today = stringResource(R.string.due_today),
        tomorrow = stringResource(R.string.due_tomorrow),
        yesterday = stringResource(R.string.due_yesterday),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val currentProject = state.path.lastOrNull()
    // Each view starts at the top of its own list.
    val listState = key(currentProject?.id) { rememberLazyListState() }
    val captureFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    var captureText by remember { mutableStateOf("") }
    var scrollToTaskId by remember { mutableStateOf<Long?>(null) }
    var rescheduleTarget by remember { mutableStateOf<TaskEntity?>(null) }
    var datePickTarget by remember { mutableStateOf<TaskEntity?>(null) }
    var editTarget by remember { mutableStateOf<TaskEntity?>(null) }

    // Leaving the screen (or the app) is a view change: rows completed here are gone when the user returns.
    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { viewModel.onLeave() }
    }

    BackHandler(enabled = currentProject != null) { viewModel.navigateUp() }

    LaunchedEffect(focusCaptureRequests) {
        if (focusCaptureRequests > 0) {
            viewModel.navigateHome()
            captureFocus.requestFocus()
            keyboard?.show()
        }
    }

    LaunchedEffect(state.rows, scrollToTaskId, currentProject?.id) {
        val index = if (currentProject == null) -1 else state.rows.indexOfFirst { it.task.id == scrollToTaskId }
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

    fun capture() {
        val text = captureText
        if (text.isBlank()) return
        captureText = ""
        viewModel.capture(text) { id -> if (currentProject != null) scrollToTaskId = id }
    }

    // The capture bar handles the status bar itself; the list stays above the keyboard and navigation bar.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding().imePadding()) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            currentProject?.let { ProjectHeader(it.title, onBack = viewModel::navigateUp) }
            CaptureBar(
                hint = currentProject?.let { stringResource(R.string.capture_hint_project, it.title) }
                    ?: stringResource(R.string.capture_hint),
                belowStatusBar = currentProject == null,
                text = captureText,
                onTextChange = { captureText = it },
                onSend = ::capture,
                onSyncNow = viewModel::refresh,
                onSignOut = viewModel::requestSignOut,
                focusRequester = captureFocus,
            )
            if (state.tokenRejected) TokenRejectedBanner(onEnterNewToken)
            StatusLine(state.status)

            if (currentProject == null) {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    when {
                        state.tiles.isNotEmpty() -> ProjectTiles(state.tiles, viewModel::openProject, Modifier.fillMaxSize())
                        !state.loaded || !state.syncedOnce -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.no_projects)) }
                    }
                }
            } else {
                // In a project, tasks take two thirds and subproject tiles the lower third.
                val hasTiles = state.tiles.isNotEmpty()
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth().weight(if (hasTiles) 2f else 1f),
                ) {
                    when {
                        state.rows.isNotEmpty() -> LazyColumn(
                            state = listState,
                            contentPadding = if (hasTiles) PaddingValues() else WindowInsets.navigationBars.asPaddingValues(),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.rows, key = { it.task.id }) { row ->
                                SwipeRow(
                                    onSwipedRight = { viewModel.complete(row.task) },
                                    onSwipedLeft = { rescheduleTarget = row.task },
                                    enabled = !row.completed,
                                    modifier = Modifier.animateItem(),
                                ) {
                                    TaskRowItem(
                                        row = row,
                                        dueLabel = row.dueDate?.let { DueLabels.label(it, state.today, words, locale) },
                                        onCheckedChange = { checked ->
                                            if (checked) viewModel.complete(row.task) else viewModel.uncomplete(row.task)
                                        },
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
                                Box(Modifier.fillMaxWidth().padding(top = 48.dp), Alignment.Center) {
                                    Text(stringResource(R.string.empty_list), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (hasTiles) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ProjectTiles(state.tiles, onOpen = viewModel::openProject, modifier = Modifier.fillMaxWidth().weight(1f))
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
            onTitleChange = { viewModel.rename(task, it) },
            onDateChange = { viewModel.reschedule(task, it) },
            onProjectChange = { viewModel.move(task, it) },
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
    hint: String,
    belowStatusBar: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    focusRequester: FocusRequester,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (belowStatusBar) Modifier.statusBarsPadding() else Modifier)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    text = { Text(stringResource(R.string.app_info)) },
                    onClick = {
                        menuOpen = false
                        infoOpen = true
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
    if (infoOpen) {
        AlertDialog(
            onDismissRequest = { infoOpen = false },
            title = { Text(stringResource(R.string.app_info)) },
            text = { Text(stringResource(R.string.app_version, appVersion)) },
            confirmButton = { TextButton(onClick = { infoOpen = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

/** Title row of a project screen; the back arrow goes to the parent project or the main view. */
@Composable
private fun ProjectHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
            Icon(
                painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(30.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Project tiles: title and the number of open tasks directly in the project. Scrolls on its own. */
@Composable
private fun ProjectTiles(tiles: List<ProjectTile>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + navigationBar),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(tiles, key = { it.project.id }) { tile ->
            OutlinedCard(onClick = { onOpen(tile.project.id) }) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        tile.project.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(R.plurals.tile_open_count, tile.openCount, tile.openCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
private fun TaskRowItem(row: TaskRow, dueLabel: String?, onCheckedChange: (Boolean) -> Unit, onOpen: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !row.completed, onClick = onOpen)
                .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .toggleable(value = row.completed, role = Role.Checkbox, onValueChange = onCheckedChange)
                    .semantics { contentDescription = row.task.title },
                contentAlignment = Alignment.Center,
            ) {
                if (row.completed) {
                    Box(
                        Modifier.size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    Box(Modifier.size(22.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                }
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    row.task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (row.completed) TextDecoration.LineThrough else null,
                    ),
                    color = if (row.completed) muted else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                row.projectTitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
            dueLabel?.let {
                Spacer(Modifier.width(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (row.overdue) MaterialTheme.colorScheme.error else muted,
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun dateLabel(date: LocalDate?, today: LocalDate, words: DueWords, locale: Locale): String =
    date?.let { DueLabels.label(it, today, words, locale) } ?: stringResource(R.string.edit_no_date)
