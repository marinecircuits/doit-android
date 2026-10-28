@file:OptIn(ExperimentalMaterial3Api::class)

package com.marinecircuits.doit.ui.tasks

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marinecircuits.doit.R
import com.marinecircuits.doit.data.CaptureSyntax
import com.marinecircuits.doit.data.local.LabelEntity
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
    val doingWarning = stringResource(R.string.doing_warning, state.doingCount)
    val doingReview = stringResource(R.string.doing_review)
    val openLabel = state.openLabel
    val isContext = state.contextScreen
    var labelPickerOpen by remember { mutableStateOf(false) }
    var labelPickerSlot by remember { mutableStateOf<Int?>(null) }
    val currentProject = state.path.lastOrNull()
    // Each view starts at the top of its own list.
    val listState = key(Triple(currentProject?.id, openLabel?.id, state.contextIndex)) { rememberLazyListState() }
    val captureFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    var captureText by remember { mutableStateOf(TextFieldValue()) }
    var captureFocused by remember { mutableStateOf(false) }
    var captureDueDate by remember { mutableStateOf<LocalDate?>(null) }
    var pickingCaptureDate by remember { mutableStateOf(false) }
    var scrollToTaskId by remember { mutableStateOf<Long?>(null) }
    var rescheduleTarget by remember { mutableStateOf<TaskEntity?>(null) }
    var datePickTarget by remember { mutableStateOf<TaskEntity?>(null) }
    var editTarget by remember { mutableStateOf<TaskEntity?>(null) }
    var colorOpen by remember { mutableStateOf(false) }
    var shoppingOpen by remember { mutableStateOf(false) }
    var recentOpen by remember { mutableStateOf(false) }
    val recent by viewModel.recentTasks.collectAsStateWithLifecycle()

    if (labelPickerOpen) {
        val slot = labelPickerSlot
        if (slot == null) {
            ContextLabelSettings(state.labels, state.contextLabelIds, onChoose = { labelPickerSlot = it }) {
                labelPickerOpen = false
            }
        } else {
            ContextLabelPicker(state.labels, state.contextLabelIds.getOrNull(slot), onSelect = { id ->
                viewModel.setContextLabel(slot, id)
                labelPickerSlot = null
            }) { labelPickerSlot = null }
        }
    }

    // Leaving the screen (or the app) is a view change: rows completed here are gone when the user returns.
    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { viewModel.onLeave() }
    }

    BackHandler(enabled = isContext || openLabel != null || currentProject != null) { viewModel.navigateUp() }

    LaunchedEffect(focusCaptureRequests) {
        if (focusCaptureRequests > 0) {
            viewModel.navigateHome()
            captureFocus.requestFocus()
            keyboard?.show()
        }
    }

    LaunchedEffect(state.rows, scrollToTaskId, currentProject?.id, openLabel?.id) {
        val index = if (currentProject == null && openLabel == null) -1 else state.rows.indexOfFirst { it.task.id == scrollToTaskId }
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

    // A new or changed conflicting count produces one overlay; unchanged syncs do not repeat it.
    LaunchedEffect(state.doingCount, state.doingLabel?.id) {
        if (state.doingCount > 1 && state.doingLabel != null) {
            val result = snackbarHostState.showSnackbar(doingWarning, actionLabel = doingReview, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.reviewDoing()
        }
    }

    fun capture() {
        val text = captureText.text
        if (text.isBlank()) return
        val dueDate = captureDueDate
        captureText = TextFieldValue()
        captureDueDate = null
        viewModel.capture(text, dueDate) { id -> if (currentProject != null || openLabel != null) scrollToTaskId = id }
    }

    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val page = openLabel?.let { ProjectColors.tile(it.hexColor) }
        ?: currentProject?.let { ProjectColors.tile(it.hexColor) }
    val colorScheme = if (page == null) {
        MaterialTheme.colorScheme
    } else {
        val container = Color(page.container)
        val content = Color(page.content)
        MaterialTheme.colorScheme.copy(
            background = container,
            surface = container,
            surfaceVariant = container,
            onBackground = content,
            onSurface = content,
            onSurfaceVariant = content.copy(alpha = 0.75f),
            outline = content.copy(alpha = 0.55f),
            outlineVariant = content.copy(alpha = 0.28f),
        )
    }
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    SideEffect {
        if (view.isInEditMode) return@SideEffect
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val lightBackground = if (page == null) !darkTheme else page.content == ProjectColors.DARK_TEXT
        controller.isAppearanceLightStatusBars = lightBackground
        controller.isAppearanceLightNavigationBars = lightBackground
    }

    // The capture bar handles the status bar itself; the list stays above the keyboard and navigation bar.
    // An open project fills the page with the same lightened colour as its tile.
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes) {
    Scaffold(
        containerColor = colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding().imePadding()) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().pointerInput(isContext, state.contextLabelIds, currentProject, openLabel) {
            var drag = 0f
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = {
                    if (drag > 100f) viewModel.nextContext()
                    if (drag < -100f && isContext) viewModel.previousContext()
                    drag = 0f
                },
                onHorizontalDrag = { change, amount ->
                    drag += amount
                    if ((isContext || (currentProject == null && openLabel == null)) && kotlin.math.abs(drag) > 12f) change.consume()
                },
            )
        }) {
            val headerTitle = openLabel?.title ?: if (isContext) stringResource(R.string.context_title) else currentProject?.title
            headerTitle?.let { ProjectHeader(if (currentProject != null && currentProject.id in state.shoppingProjects) "🛒 $it" else it, onBack = viewModel::navigateUp, onHome = viewModel::navigateHome) }
            state.doingTask?.let { row ->
                DoingTop(
                    row = row,
                    count = state.doingCount,
                    onOpen = {
                        if (state.doingCount > 1) viewModel.reviewDoing() else editTarget = row.task
                    },
                    modifier = if (headerTitle == null) Modifier.statusBarsPadding() else Modifier,
                )
            }
            CaptureBar(
                hint = currentProject?.let { stringResource(R.string.capture_hint_project, it.title) }
                    ?: stringResource(R.string.capture_hint),
                belowStatusBar = headerTitle == null && state.doingTask == null,
                text = captureText,
                onTextChange = { value ->
                    val withoutTrigger = DateTrigger.consume(captureText, value)
                    captureText = withoutTrigger ?: value
                    if (withoutTrigger != null) pickingCaptureDate = true
                },
                onFocusChange = { captureFocused = it },
                onSend = ::capture,
                onSignOut = viewModel::requestSignOut,
                onRecent = { viewModel.openRecentTasks(); recentOpen = true },
                onConfigureContext = { labelPickerOpen = true },
                showContextOption = currentProject == null && (openLabel == null || isContext),
                onProjectColor = currentProject?.let { { colorOpen = true } },
                onProjectSettings = currentProject?.let { { shoppingOpen = true } },
                serverAddress = state.serverAddress,
                status = state.status,
                syncedOnce = state.syncedOnce,
                tokenRejected = state.tokenRejected,
                syncError = state.syncError,
                focusRequester = captureFocus,
            )
            captureDueDate?.let { selected ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickingCaptureDate = true }) {
                        Text(stringResource(R.string.capture_due_date, selected.toString()))
                    }
                    TextButton(onClick = { captureDueDate = null }) { Text(stringResource(R.string.edit_clear_date)) }
                }
            }
            val suggestions = if (captureFocused && captureText.selection.collapsed) {
                CaptureSyntax.suggestionsAt(captureText.text, captureText.selection.end, state.labels, state.projects)
            } else {
                emptyList()
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize()) {
                    if (state.tokenRejected) TokenRejectedBanner(onEnterNewToken)
                    StatusLine(state.status)

                    if (openLabel == null && currentProject == null) {
                        PullToRefreshBox(
                            isRefreshing = state.refreshing,
                            onRefresh = viewModel::refresh,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        ) {
                            when {
                                state.tiles.isNotEmpty() || state.labels.isNotEmpty() || (state.loaded && state.syncedOnce) -> ProjectTiles(
                                    state.tiles,
                                    viewModel::openProject,
                                    Modifier.fillMaxSize(),
                                    largeTiles = true,
                                    homeLabels = state.labels,
                                    shoppingProjects = state.shoppingProjects,
                                    onOpenLabel = viewModel::openLabel,
                                )
                                else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    } else {
                        // In a project, tasks take two thirds and subproject tiles the lower third.
                        val hasTiles = openLabel == null && state.tiles.isNotEmpty()
                        PullToRefreshBox(
                            isRefreshing = state.refreshing,
                            onRefresh = viewModel::refresh,
                            modifier = Modifier.fillMaxWidth().weight(if (hasTiles) 2f else 1f),
                        ) {
                            when {
                                state.rows.isNotEmpty() -> LazyColumn(
                                    state = listState,
                                    contentPadding = if (hasTiles) {
                                        PaddingValues()
                                    } else {
                                        WindowInsets.navigationBars.asPaddingValues()
                                    },
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
                                                dueLabel = row.dueDate?.let {
                                                    DueLabels.label(it, state.today, words, locale)
                                                },
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        viewModel.complete(row.task)
                                                    } else {
                                                        viewModel.uncomplete(row.task)
                                                    }
                                                },
                                                onOpen = { editTarget = row.task },
                                                working = row.task.id in state.doingTaskIds,
                                                onWork = if (state.doingLabel != null && !row.completed && row.task.projectId !in state.shoppingProjects) {
                                                    { viewModel.setDoing(row.task, row.task.id !in state.doingTaskIds) }
                                                } else null,
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
                                            Text(
                                                stringResource(R.string.empty_list),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (hasTiles) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ProjectTiles(
                                state.tiles,
                                onOpen = viewModel::openProject,
                                shoppingProjects = state.shoppingProjects,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        }
                    }
                }
                // Over the content, lined up with the capture field, so nothing below it moves.
                if (suggestions.isNotEmpty()) {
                    CaptureSuggestions(
                        suggestions,
                        onPick = { suggestion ->
                            val value = captureText
                            CaptureSyntax.complete(value.text, value.selection.end, suggestion.title)?.let {
                                captureText = TextFieldValue(it.text, TextRange(it.cursor))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 52.dp),
                    )
                }
            }
            if (captureFocused) {
                SyntaxKeyRow(
                    onInsert = { token -> captureText = SyntaxKeys.insert(captureText, token) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    onPickDate = { pickingCaptureDate = true },
                )
            }
        }
    }
    }

    if (pickingCaptureDate) {
        PickDateDialog(
            initial = captureDueDate ?: state.today,
            onPicked = { date -> captureDueDate = date; pickingCaptureDate = false },
            onDismiss = { pickingCaptureDate = false },
        )
    }

    if (recentOpen) {
        RecentTasksDialog(recent, state.projects.associate { it.id to it.title },
            onUndo = viewModel::undoRecentTask, onDismiss = { recentOpen = false })
    }

    if (shoppingOpen && currentProject != null) {
        AlertDialog(
            onDismissRequest = { shoppingOpen = false },
            title = { Text(stringResource(R.string.project_settings)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(currentProject.title, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.shopping_list), Modifier.weight(1f))
                        Switch(checked = currentProject.id in state.shoppingProjects, onCheckedChange = viewModel::setShoppingProject)
                    }
                    Text(stringResource(R.string.shopping_explanation))
                    Text(stringResource(R.string.shopping_quantity_hint))
                }
            },
            confirmButton = { TextButton(onClick = { shoppingOpen = false }) { Text(stringResource(R.string.ok)) } },
        )
    }

    if (colorOpen) {
        ProjectColorDialog(
            selected = currentProject?.hexColor.orEmpty(),
            onPick = { hex ->
                colorOpen = false
                viewModel.setProjectColor(hex)
            },
            onDismiss = { colorOpen = false },
        )
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
            labels = state.labels,
            shoppingProjects = state.shoppingProjects,
            today = state.today,
            words = words,
            locale = locale,
            onTitleChange = { viewModel.applyEditedTitle(task, it) },
            onDateChange = { viewModel.reschedule(task, it) },
            onProjectChange = { viewModel.move(task, it) },
            onDelete = {
                viewModel.delete(task)
                editTarget = null
            },
            working = task.id in state.doingTaskIds,
            onWork = if (state.doingLabel != null && !task.done) {
                { viewModel.setDoing(task, task.id !in state.doingTaskIds) }
            } else null,
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

/** A persistent shortcut to the current working task or the complete doing list. */
@Composable
private fun DoingTop(row: TaskRow, count: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_start_work), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.doing_top_title), style = MaterialTheme.typography.labelMedium)
                Text(row.task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                row.projectTitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (count > 1) {
                Spacer(Modifier.width(8.dp))
                Text(pluralStringResource(R.plurals.doing_more, count - 1, count - 1), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ContextLabelSettings(
    labels: List<com.marinecircuits.doit.data.local.LabelEntity>,
    selected: List<Long?>,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_context_label)) },
        text = {
            Column {
                repeat(3) { slot ->
                    val name = labels.firstOrNull { it.id == selected.getOrNull(slot) }?.title
                        ?: stringResource(R.string.context_not_configured)
                    TextButton(onClick = { onChoose(slot) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.context_slot, slot + 1, name))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun ContextLabelPicker(labels: List<com.marinecircuits.doit.data.local.LabelEntity>, selected: Long?, onSelect: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_context_label)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item { TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.context_disabled)) } }
                items(labels, key = { it.id }) { label ->
                    TextButton(onClick = { onSelect(label.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (selected == label.id) "✓ " else "") + label.title)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CaptureBar(
    hint: String,
    belowStatusBar: Boolean,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    onSignOut: () -> Unit,
    onRecent: () -> Unit,
    onConfigureContext: () -> Unit,
    showContextOption: Boolean,
    onProjectColor: (() -> Unit)?,
    onProjectSettings: (() -> Unit)?,
    serverAddress: String?,
    status: List<StatusPart>,
    syncedOnce: Boolean,
    tokenRejected: Boolean,
    syncError: String?,
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
                IconButton(onClick = onSend, enabled = text.text.isNotBlank()) {
                    Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.capture_send))
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.recent_tasks)) },
                    onClick = { menuOpen = false; onRecent() },
                )
                if (showContextOption) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_context_label)) },
                        onClick = { menuOpen = false; onConfigureContext() },
                    )
                }
                if (onProjectColor != null) {
                    if (onProjectSettings != null) DropdownMenuItem(
                        text = { Text(stringResource(R.string.project_settings)) },
                        onClick = { menuOpen = false; onProjectSettings() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_color)) },
                        onClick = {
                            menuOpen = false
                            onProjectColor()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (SyncStatus.needsAttention(status, tokenRejected)) R.string.app_info_issue else R.string.app_info))
                        },
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
    }
    if (infoOpen) {
        AlertDialog(
            onDismissRequest = { infoOpen = false },
            title = { Text(stringResource(R.string.app_info)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.app_version, appVersion))
                    Text(stringResource(R.string.server_info_address, serverAddress ?: stringResource(R.string.server_info_unavailable)))
                    when {
                        tokenRejected -> Text(stringResource(R.string.token_rejected_banner))
                        status.isNotEmpty() -> StatusLine(status)
                        syncedOnce -> Text(stringResource(R.string.server_info_synced))
                        else -> Text(stringResource(R.string.server_info_not_synced))
                    }
                    if (!syncError.isNullOrBlank() && !tokenRejected) {
                        Text(stringResource(R.string.server_info_error_detail, syncError))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { infoOpen = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

/** The fixed palette, plus no colour. Choosing one closes the dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectColorDialog(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val current = ProjectColors.parse(selected)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.project_color_title)) },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProjectColors.PALETTE.forEach { hex ->
                    ColorSwatch(
                        hex = hex,
                        selected = ProjectColors.parse(hex) == current,
                        onClick = { onPick(hex) },
                    )
                }
                ColorSwatch(
                    hex = "",
                    selected = selected.isBlank(),
                    onClick = { onPick("") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ProjectColors.tile(hex)
    val description = if (hex.isEmpty()) stringResource(R.string.project_color_none) else "#$hex"
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors?.let { Color(it.container) } ?: Color.Transparent)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    )
}

/** Title row of a project screen; the back arrow goes to the parent project or the main view. */
@Composable
private fun ProjectHeader(title: String, onBack: () -> Unit, onHome: () -> Unit) {
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
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(onClick = onHome) { Text(stringResource(R.string.home)) }
    }
}

/**
 * Project tiles: title and the number of open tasks directly in the project, filled with the project's colour made
 * lighter, or plain without one. Scrolls on its own.
 */
@Composable
private fun ProjectTiles(
    tiles: List<ProjectTile>,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    largeTiles: Boolean = false,
    homeLabels: List<LabelEntity>? = null,
    shoppingProjects: Set<Long> = emptySet(),
    onOpenLabel: (Long) -> Unit = {},
) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + navigationBar),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        if (homeLabels != null && tiles.isEmpty()) {
            item(key = "no-projects", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), Alignment.Center) {
                    Text(stringResource(R.string.no_projects), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(tiles, key = { it.project.id }) { tile ->
            val colors = ProjectColors.tile(tile.project.hexColor)
            OutlinedCard(
                onClick = { onOpen(tile.project.id) },
                colors = if (colors == null) {
                    CardDefaults.outlinedCardColors()
                } else {
                    CardDefaults.outlinedCardColors(
                        containerColor = Color(colors.container),
                        contentColor = Color(colors.content),
                    )
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().then(
                        if (largeTiles) Modifier.heightIn(min = 104.dp) else Modifier
                    ).padding(12.dp),
                    verticalArrangement = if (largeTiles) Arrangement.Center else Arrangement.Top,
                ) {
                    Text(
                        if (tile.project.id in shoppingProjects) "🛒 ${tile.project.title}" else tile.project.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(R.plurals.tile_open_count, tile.openCount, tile.openCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors?.let { Color(it.content).copy(alpha = 0.8f) }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (homeLabels != null) {
            item(key = "labels-heading", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 16.dp))
                    Text(stringResource(R.string.home_labels), style = MaterialTheme.typography.titleSmall)
                }
            }
            item(key = "labels", span = { GridItemSpan(maxLineSpan) }) {
                if (homeLabels.isEmpty()) {
                    Text(stringResource(R.string.no_labels), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    HomeLabelShortcuts(homeLabels, onOpenLabel)
                }
            }
        }
    }
}

/** Compact coloured label links share the home grid's scroll area with projects. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeLabelShortcuts(labels: List<LabelEntity>, onOpen: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.sortedWith(compareBy<LabelEntity> { it.title.lowercase() }.thenBy { it.id }).forEach { label ->
            val colors = ProjectColors.tile(label.hexColor)
            Surface(
                onClick = { onOpen(label.id) },
                shape = CircleShape,
                color = colors?.let { Color(it.container) } ?: MaterialTheme.colorScheme.surfaceVariant,
                contentColor = colors?.let { Color(it.content) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("@${label.title}", style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun TaskRowItem(
    row: TaskRow,
    dueLabel: String?,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    working: Boolean,
    onWork: (() -> Unit)?,
) {
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
                    row.displayTitle,
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
                if (row.labels.isNotEmpty()) {
                    LabelChips(row.labels, dimmed = row.completed, modifier = Modifier.padding(top = 4.dp))
                }
            }
            row.quantity?.let { quantity ->
                Text(quantity, modifier = Modifier.padding(start = 8.dp).width(90.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)).padding(6.dp), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            dueLabel?.let {
                Spacer(Modifier.width(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (row.overdue) MaterialTheme.colorScheme.error else muted,
                )
            }
            if (onWork != null) {
                IconButton(onClick = onWork) {
                    Icon(
                        painterResource(if (working) R.drawable.ic_stop_work else R.drawable.ic_start_work),
                        contentDescription = stringResource(if (working) R.string.stop_working else R.string.start_working),
                        tint = if (working) MaterialTheme.colorScheme.primary else muted,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun dateLabel(date: LocalDate?, today: LocalDate, words: DueWords, locale: Locale): String =
    date?.let { DueLabels.label(it, today, words, locale) } ?: stringResource(R.string.edit_no_date)

/** Server completions and this device's deletions have different sources. */
@Composable
private fun RecentTasksDialog(
    state: RecentTasksState, projects: Map<Long, String>,
    onUndo: (com.marinecircuits.doit.data.local.TaskHistoryEntity, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var completedLimit by remember { mutableIntStateOf(5) }
    var deletedLimit by remember { mutableIntStateOf(5) }
    var selected by remember { mutableStateOf<com.marinecircuits.doit.data.local.TaskHistoryEntity?>(null) }
    var undoing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(56.dp)) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.recent_tasks), style = MaterialTheme.typography.titleLarge)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    item { Text(stringResource(R.string.recent_completed), style = MaterialTheme.typography.titleMedium) }
                    if (state.loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                    if (state.error) item { Text(stringResource(R.string.recent_offline), modifier = Modifier.padding(vertical = 8.dp)) }
                    if (!state.loading && state.completed.isEmpty()) item { Text(stringResource(R.string.recent_empty), Modifier.padding(vertical = 12.dp)) }
                    items(state.completed.take(completedLimit), key = { "completed-${it.taskId}" }) { task ->
                        HistoryRow(task, projects) { if (!state.loading && !undoing) selected = task }
                    }
                    if (state.completed.size > completedLimit) item {
                        TextButton(onClick = { completedLimit += 5 }) { Text(stringResource(R.string.recent_load_more)) }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 16.dp)); Text(stringResource(R.string.recent_deleted), style = MaterialTheme.typography.titleMedium) }
                    item { Text(stringResource(R.string.recent_deleted_scope), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
                    if (state.deleted.isEmpty()) item { Text(stringResource(R.string.recent_empty), Modifier.padding(vertical = 12.dp)) }
                    items(state.deleted.take(deletedLimit), key = { "deleted-${it.id}" }) { task ->
                        HistoryRow(task, projects) { if (!undoing) selected = task }
                    }
                    if (state.deleted.size > deletedLimit) item {
                        TextButton(onClick = { deletedLimit += 5 }) { Text(stringResource(R.string.recent_load_more)) }
                    }
                }
            }
        }
    }
    selected?.let { task ->
        val deleted = task.kind == com.marinecircuits.doit.data.local.TaskHistoryEntity.DELETED
        AlertDialog(
            onDismissRequest = { if (!undoing) selected = null },
            title = { Text(task.title) },
            text = { Text(stringResource(if (deleted) R.string.recent_restore_details else R.string.recent_reopen_details)) },
            confirmButton = {
                TextButton(enabled = !undoing, onClick = {
                    undoing = true
                    onUndo(task) { success -> undoing = false; selected = null; failed = !success }
                }) { Text(stringResource(if (deleted) R.string.recent_restore else R.string.recent_reopen)) }
            },
            dismissButton = { TextButton(enabled = !undoing, onClick = { selected = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (failed) AlertDialog(
        onDismissRequest = { failed = false }, title = { Text(stringResource(R.string.recent_undo_failed)) },
        confirmButton = { TextButton(onClick = { failed = false }) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun HistoryRow(task: com.marinecircuits.doit.data.local.TaskHistoryEntity, projects: Map<Long, String>, onClick: () -> Unit) {
    val context = LocalContext.current
    val date = remember(task.happenedAt, context) {
        if (task.happenedAt <= 0) null else android.text.format.DateUtils.getRelativeTimeSpanString(
            task.happenedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(task.title, style = MaterialTheme.typography.bodyLarge)
        val details = listOfNotNull(projects[task.projectId], date).joinToString(" · ")
        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}
