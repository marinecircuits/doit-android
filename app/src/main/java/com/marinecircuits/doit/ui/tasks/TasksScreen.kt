@file:OptIn(ExperimentalMaterial3Api::class)

package com.marinecircuits.doit.ui.tasks

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.ui.text.font.FontWeight
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
import com.marinecircuits.doit.ui.theme.DoitWordmark
import com.marinecircuits.doit.data.Recurrence
import com.marinecircuits.doit.container
import com.marinecircuits.doit.data.CaptureSyntax
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.isInbox
import com.marinecircuits.doit.data.local.AttachmentEntity
import com.marinecircuits.doit.data.local.TaskEntity
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The main view and every project screen: capture box, project tiles, a task list in projects,
 * and the sheets for rescheduling and editing. The sync status sits at the bottom so it does not push the list down.
 * [focusCaptureRequests] grows each time the widget's + button asks
 * for the capture box, which always opens the main view.
 */
@Composable
fun TasksScreen(viewModel: TasksViewModel, focusCaptureRequests: Int, onEnterNewToken: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedFilters by viewModel.savedFilters.collectAsStateWithLifecycle()
    val savedFilterRows by viewModel.savedFilterRows.collectAsStateWithLifecycle()
    val appContext = LocalContext.current
    val locale = LocalLocale.current.platformLocale
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
    var nextActionsPickerOpen by remember { mutableStateOf(false) }
    var labelPickerSlot by remember { mutableStateOf<Int?>(null) }
    val currentProject = state.path.lastOrNull()
    // Each view starts at the top of its own list.
    val listState = key(Triple(currentProject?.id, openLabel?.id, state.contextIndex)) { rememberLazyListState() }
    val captureFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var captureText by remember { mutableStateOf(TextFieldValue()) }
    var captureFocused by remember { mutableStateOf(false) }
    val captureSchedule = remember { ScheduleState() }
    captureSchedule.changeText = { captureText = it }
    var scrollToTaskId by remember { mutableStateOf<Long?>(null) }
    var editTarget by remember { mutableStateOf<TaskEntity?>(null) }
    var selectedTaskIds by remember { mutableStateOf(setOf<Long>()) }
    var bulkMoveOpen by remember { mutableStateOf(false) }
    fun toggleSelection(id: Long) {
        selectedTaskIds = if (id in selectedTaskIds) selectedTaskIds - id else selectedTaskIds + id
    }
    val scope = rememberCoroutineScope()
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val task = editTarget ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = appContext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }?.ifBlank { null } ?: "attachment"
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            viewModel.addPdf(task.id, name, bytes, appContext.contentResolver.getType(uri) ?: "application/octet-stream")
        }
    }
    val pdfs by produceState(emptyList<AttachmentEntity>(), editTarget?.id) {
        val id = editTarget?.id
        if (id == null) { value = emptyList(); return@produceState }
        viewModel.attachments(id).collect { value = it }
    }
    var colorOpen by remember { mutableStateOf(false) }
    var shoppingOpen by remember { mutableStateOf(false) }
    var archiveOpen by remember { mutableStateOf(false) }
    var deletingProjectId by remember { mutableStateOf<Long?>(null) }
    var recentOpen by remember { mutableStateOf(false) }
    var selectedSearchIndex by remember { mutableIntStateOf(0) }
    val searchQuery = TaskSearch.query(captureText.text)
    val searchResults = searchQuery?.let { TaskSearch.matches(it, state.searchRows, state.projects, state.labels) }.orEmpty()
    fun openSearchResult(result: TaskSearch.Result) {
        captureText = TextFieldValue()
        selectedSearchIndex = 0
        when (result.kind) {
            TaskSearch.Kind.PROJECT -> viewModel.openSearchProject(result.id)
            TaskSearch.Kind.TASK -> editTarget = state.searchRows.firstOrNull { it.task.id == result.id }?.task
            TaskSearch.Kind.LABEL -> viewModel.openLabel(result.id)
        }
    }
    val recent by viewModel.recentTasks.collectAsStateWithLifecycle()
    val subproject by viewModel.subproject.collectAsStateWithLifecycle()
    subproject?.let { draft ->
        CreateSubprojectDialog(draft, viewModel::setSubprojectTitle, viewModel::submitSubproject, viewModel::dismissCreateSubproject)
    }

    if (labelPickerOpen) {
        val slot = labelPickerSlot
        if (slot == null) {
            ContextLabelSettings(state.labels, state.contextLabelIds, state.swipeDownLabelId, onChoose = { labelPickerSlot = it }) {
                labelPickerOpen = false
            }
        } else {
            ContextLabelPicker(state.labels, if (slot == 3) state.swipeDownLabelId else state.contextLabelIds.getOrNull(slot),
                title = stringResource(R.string.menu_context_label), noneText = stringResource(R.string.context_disabled), onSelect = { id ->
                if (slot == 3) viewModel.setSwipeDownLabel(id) else viewModel.setContextLabel(slot, id)
                labelPickerSlot = null
            }) { labelPickerSlot = null }
        }
    }

    if (nextActionsPickerOpen) {
        ContextLabelPicker(state.labels, state.nextActionsLabelId,
            title = stringResource(R.string.next_actions_label), noneText = stringResource(R.string.next_actions_none),
            onSelect = { id ->
                nextActionsPickerOpen = false
                viewModel.setNextActionsLabel(id) { appContext.container.refreshWidgets() }
            }) { nextActionsPickerOpen = false }
    }

    // Leaving the screen (or the app) is a view change: rows completed here are gone when the user returns.
    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { viewModel.onLeave() }
    }

    BackHandler(enabled = selectedTaskIds.isNotEmpty()) {
        selectedTaskIds = emptySet()
        bulkMoveOpen = false
    }
    BackHandler(enabled = selectedTaskIds.isEmpty() && (savedFilters.open || isContext || openLabel != null || currentProject != null)) {
        viewModel.navigateUp()
    }
    LaunchedEffect(currentProject?.id, openLabel?.id, isContext, savedFilters.open) {
        selectedTaskIds = emptySet()
        bulkMoveOpen = false
    }
    LaunchedEffect(state.rows) {
        selectedTaskIds = selectedTaskIds.intersect(state.rows.map { it.task.id }.toSet())
    }
    BackHandler(enabled = searchQuery != null) { captureText = TextFieldValue(); selectedSearchIndex = 0 }

    LaunchedEffect(savedFilters.open) {
        if (savedFilters.open) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

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
        if (searchQuery != null) {
            searchResults.getOrNull(selectedSearchIndex.coerceIn(0, (searchResults.size - 1).coerceAtLeast(0)))?.let(::openSearchResult)
            return
        }
        if (!captureSchedule.prepare(captureText)) return
        val text = captureText.text
        if (text.isBlank()) return
        if (TaskSearch.filter(text) != null) {
            captureText = TextFieldValue("/$text", TextRange(text.length + 1))
            selectedSearchIndex = 0
            return
        }
        val dueDate = captureSchedule.day
        val repeat = captureSchedule.rule
        captureText = TextFieldValue()
        captureSchedule.reset()
        viewModel.capture(text, dueDate, repeat) { id -> if (currentProject != null || openLabel != null) scrollToTaskId = id }
    }

    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val page = openLabel?.let { ProjectColors.page(it.hexColor, darkTheme) }
        ?: currentProject?.let { ProjectColors.page(it.hexColor, darkTheme) }
    val colorScheme = if (page == null) {
        MaterialTheme.colorScheme
    } else {
        val container = Color(page.container)
        val content = Color(page.content)
        MaterialTheme.colorScheme.copy(
            background = container,
            surface = container,
            surfaceVariant = if (darkTheme) MaterialTheme.colorScheme.surfaceVariant else container,
            onBackground = content,
            onSurface = content,
            onSurfaceVariant = if (darkTheme) MaterialTheme.colorScheme.onSurfaceVariant else content.copy(alpha = 0.75f),
            outline = if (darkTheme) MaterialTheme.colorScheme.outline else content.copy(alpha = 0.55f),
            outlineVariant = if (darkTheme) MaterialTheme.colorScheme.outlineVariant else content.copy(alpha = 0.28f),
        )
    }
    val view = LocalView.current
    SideEffect {
        if (view.isInEditMode) return@SideEffect
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val lightBackground = if (page == null) !darkTheme else page.content == ProjectColors.DARK_TEXT
        controller.isAppearanceLightStatusBars = lightBackground
        controller.isAppearanceLightNavigationBars = lightBackground
    }

    // Home/project headers handle the status bar; search gives that space to the capture field.
    // Dark project pages use a quieter tint so their task cards remain distinct.
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val canPage = savedFilters.open || (searchQuery == null && !captureFocused &&
        (isContext || (currentProject == null && openLabel == null)))
    if (bulkMoveOpen && selectedTaskIds.isNotEmpty()) {
        BulkMoveDialog(
            count = selectedTaskIds.size,
            projects = state.projects,
            onDismiss = { bulkMoveOpen = false },
            onMove = { projectId ->
                viewModel.moveTasks(selectedTaskIds.toList(), projectId)
                selectedTaskIds = emptySet()
                bulkMoveOpen = false
            },
        )
    }
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes) {
    Scaffold(
        containerColor = colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding().imePadding()) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().pointerInput(canPage, savedFilters.open,
            savedFilters.selected?.id, state.contextIndex, swipeThreshold) {
            if (!canPage) return@pointerInput
            var drag = 0f
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = {
                    if (drag > swipeThreshold) viewModel.swipeRight()
                    if (drag < -swipeThreshold) viewModel.swipeLeft()
                    drag = 0f
                },
                onDragCancel = { drag = 0f },
                onHorizontalDrag = { change, amount ->
                    drag += amount
                    change.consume()
                },
            )
        }) {
            if (savedFilters.open) {
                SavedFiltersPage(savedFilters, savedFilterRows, viewModel::openSavedFilter, viewModel::refreshSavedFilters,
                    viewModel::navigateUp, viewModel::navigateHome) { row ->
                    TaskRowItem(row = row,
                        dueLabel = row.dueDate?.let { DueLabels.label(it, state.today, words, locale) },
                        onCheckedChange = { checked -> if (checked) viewModel.complete(row.task) else viewModel.uncomplete(row.task) },
                        onOpen = { editTarget = row.task },
                        working = row.task.id in state.doingTaskIds,
                        onWork = if (state.doingLabel != null && !row.completed && row.task.projectId !in state.shoppingProjects) {
                            { viewModel.setDoing(row.task, row.task.id !in state.doingTaskIds) }
                        } else null,
                    )
                }
            } else {
            val headerTitle = openLabel?.title ?: if (isContext) stringResource(R.string.context_title) else currentProject?.title
            if (searchQuery == null && headerTitle == null) HomeHeader(state.today, locale, viewModel::openSavedFilters)
            if (searchQuery == null) headerTitle?.let { ProjectHeader(if (currentProject != null && currentProject.id in state.shoppingProjects) "🛒 $it" else it, onBack = viewModel::navigateUp, onHome = viewModel::navigateHome) }
            if (searchQuery == null && selectedTaskIds.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.bulk_selected, selectedTaskIds.size), Modifier.weight(1f))
                    TextButton(onClick = { selectedTaskIds = emptySet() }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { bulkMoveOpen = true }) { Text(stringResource(R.string.bulk_edit)) }
                }
            }
            if (searchQuery == null && selectedTaskIds.isEmpty()) state.doingTask?.let { row ->
                DoingTop(
                    row = row,
                    count = state.doingCount,
                    onOpen = {
                        if (state.doingCount > 1) viewModel.reviewDoing() else editTarget = row.task
                    },
                    modifier = Modifier,
                )
            }
            CaptureBar(
                hint = stringResource(R.string.capture_hint),
                belowStatusBar = searchQuery != null,
                text = captureText,
                onTextChange = { value ->
                    if (TaskSearch.query(value.text) != searchQuery) selectedSearchIndex = 0
                    captureText = value
                },
                onScheduleKey = { captureSchedule.key(it, captureText) },
                onSearchKey = { event ->
                    if (searchQuery == null || event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionDown -> { selectedSearchIndex = (selectedSearchIndex + 1).coerceAtMost(searchResults.lastIndex.coerceAtLeast(0)); true }
                        Key.DirectionUp -> { selectedSearchIndex = (selectedSearchIndex - 1).coerceAtLeast(0); true }
                        Key.Escape -> { captureText = TextFieldValue(); selectedSearchIndex = 0; true }
                        Key.Enter, Key.NumPadEnter -> { capture(); true }
                        else -> false
                    }
                },
                searchMode = searchQuery != null,
                hasSearchResult = searchResults.isNotEmpty(),
                onFocusChange = { captureFocused = it },
                onSend = ::capture,
                onSignOut = viewModel::requestSignOut,
                onRecent = { viewModel.openRecentTasks(); recentOpen = true },
                onConfigureContext = { labelPickerOpen = true },
                onConfigureNextActions = { nextActionsPickerOpen = true },
                showNextActionsOption = currentProject == null && openLabel == null,
                showContextOption = currentProject == null && (openLabel == null || isContext),
                onProjectColor = currentProject?.let { { colorOpen = true } },
                onProjectSettings = currentProject?.let { { shoppingOpen = true } },
                onCreateSubproject = currentProject
                    ?.takeUnless { it.isInbox(state.defaultProjectId) }
                    ?.let { viewModel::showCreateSubproject },
                onArchiveProject = currentProject?.let { { archiveOpen = true } },
                onDeleteProject = currentProject?.let { { deletingProjectId = it.id } },
                serverAddress = state.serverAddress,
                status = state.status,
                syncedOnce = state.syncedOnce,
                tokenRejected = state.tokenRejected,
                syncError = state.syncError,
                focusRequester = captureFocus,
            )
            if (searchQuery == null) ScheduleChips(captureSchedule, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            val suggestions = if (searchQuery == null && captureFocused && captureText.selection.collapsed) {
                CaptureSyntax.suggestionsAt(captureText.text, captureText.selection.end, state.labels, state.projects)
            } else {
                emptyList()
            }
            val threshold = with(LocalDensity.current) { 80.dp.toPx() }
            val downConnection = remember(state.swipeDownLabelId, state.labels, threshold) {
                object : NestedScrollConnection {
                    var pulled = 0f
                    var opened = false
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (source == NestedScrollSource.UserInput && available.y > 0 && !opened &&
                            state.labels.any { it.id == state.swipeDownLabelId }) {
                            pulled += available.y
                            if (pulled >= threshold) { opened = true; viewModel.openSwipeDownLabel() }
                        }
                        return Offset.Zero
                    }
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < 0) pulled = 0f
                        return Offset.Zero
                    }
                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        pulled = 0f
                        opened = false
                        return Velocity.Zero
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).nestedScroll(downConnection)) {
                if (searchQuery != null) {
                    SearchResults(searchQuery, searchResults, selectedSearchIndex.coerceAtMost(searchResults.lastIndex.coerceAtLeast(0)), ::openSearchResult)
                } else {
                Column(Modifier.fillMaxSize()) {
                    if (state.tokenRejected) TokenRejectedBanner(onEnterNewToken)

                    if (openLabel == null && currentProject == null) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            when {
                                state.tiles.isNotEmpty() || state.labels.isNotEmpty() || (state.loaded && state.syncedOnce) -> ProjectTiles(
                                    state.tiles,
                                    viewModel::openProject,
                                    Modifier.fillMaxSize(),
                                    largeTiles = true,
                                    homeLabels = state.labels.filterNot { it.title.equals("doing", ignoreCase = true) },
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
                        Box(modifier = Modifier.fillMaxWidth().weight(if (hasTiles) 2f else 1f)) {
                            when {
                                state.rows.isNotEmpty() -> LazyColumn(
                                    state = listState,
                                    contentPadding = if (hasTiles || state.status.isNotEmpty()) {
                                        PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    } else {
                                        PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp,
                                            bottom = 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                                    },
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(state.rows, key = { it.task.id }) { row ->
                                        Box(Modifier.animateItem()) {
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
                                                onOpen = {
                                                    if (selectedTaskIds.isNotEmpty()) toggleSelection(row.task.id)
                                                    else editTarget = row.task
                                                },
                                                onLongPress = { toggleSelection(row.task.id) },
                                                selected = row.task.id in selectedTaskIds,
                                                selecting = selectedTaskIds.isNotEmpty(),
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
                                        Column(Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Icon(painterResource(R.drawable.ic_check), contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(14.dp))
                                            Text(
                                                stringResource(R.string.empty_list),
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                            Text(stringResource(R.string.empty_list_hint), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium)
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
                if (captureFocused) ScheduleSuggestions(captureSchedule, captureText,
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 52.dp))
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
            }
            if (searchQuery == null && state.status.isNotEmpty()) {
                StatusLine(
                    state.status,
                    modifier = if (captureFocused) Modifier else Modifier.navigationBarsPadding(),
                )
            }
            if (captureFocused && searchQuery == null) {
                SyntaxKeyRow(
                    onInsert = { token -> captureText = SyntaxKeys.insert(captureText, token) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    onPickDate = { captureText = SyntaxKeys.insert(captureText, "!") },
                    includeSearch = true,
                )
            }
            }
        }
    }
    }

    ScheduleDialogs(captureSchedule)

    if (recentOpen) {
        RecentTasksDialog(recent, state.projects.associate { it.id to it.title },
            onUndo = viewModel::undoRecentTask, onDismiss = { recentOpen = false })
    }

    if (shoppingOpen && currentProject != null) {
        val projectId = currentProject.id
        var projectName by remember(projectId) { mutableStateOf(currentProject.title) }
        AlertDialog(
            onDismissRequest = { shoppingOpen = false },
            title = { Text(stringResource(R.string.project_settings)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text(stringResource(R.string.project_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.shopping_list), Modifier.weight(1f))
                        Switch(checked = currentProject.id in state.shoppingProjects, onCheckedChange = viewModel::setShoppingProject)
                    }
                    Text(stringResource(R.string.shopping_explanation))
                    Text(stringResource(R.string.shopping_quantity_hint))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameOpenProject(projectName)
                    shoppingOpen = false
                }) { Text(stringResource(R.string.ok)) }
            },
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

    editTarget?.let { task ->
        val taskLabels by remember(task.id) { viewModel.taskLabels(task.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
        EditSheet(
            task = task,
            projects = state.projects,
            labels = state.labels,
            shoppingProjects = state.shoppingProjects,
            today = state.today,
            words = words,
            locale = locale,
            onTitleChange = { viewModel.applyEditedTitle(task, it) },
            onScheduleChange = { rule, day -> viewModel.setRepeat(task, rule, day) },
            onDescriptionChange = { viewModel.setDescription(task, it) },
            onDateChange = { viewModel.reschedule(task, it) },
            onProjectChange = { viewModel.move(task, it) },
            onOpenProject = { id ->
                editTarget = null
                viewModel.openSearchProject(id)
            },
            attachments = pdfs,
            taskLabels = taskLabels,
            onRemoveLabel = { viewModel.removeTaskLabel(task.id, it) },
            onAddPdf = { pickPdf.launch(arrayOf("*/*")) },
            onOpenPdf = { id ->
                scope.launch {
                    val file = viewModel.localPdf(id) ?: return@launch
                    val uri = FileProvider.getUriForFile(appContext, appContext.packageName + ".fileprovider", file)
                    val mime = pdfs.firstOrNull { it.id == id }?.mime ?: "application/octet-stream"
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { appContext.startActivity(intent) }
                }
            },
            onRemovePdf = { id -> viewModel.removePdf(id) },
            onDelete = {
                viewModel.delete(task)
                editTarget = null
            },
            onPromote = { title, description, projectId, onDone ->
                viewModel.promoteToProject(task, title, description, projectId, onDone)
            },
            onDismiss = { editTarget = null },
        )
    }

    deletingProjectId?.let { id ->
        DeleteProjectDialog(id, appContext.container.syncEngine,
            onDeleted = { deletingProjectId = null; viewModel.navigateHome(); viewModel.refresh() },
            onDismiss = { deletingProjectId = null })
    }
    if (archiveOpen && currentProject != null) {
        val title = currentProject.title
        AlertDialog(
            onDismissRequest = { archiveOpen = false },
            title = { Text(stringResource(R.string.archive_title)) },
            text = { Text(stringResource(R.string.archive_body, title)) },
            confirmButton = {
                TextButton(onClick = {
                    archiveOpen = false
                    viewModel.archiveOpenProject()
                }) { Text(stringResource(R.string.archive_confirm)) }
            },
            dismissButton = { TextButton(onClick = { archiveOpen = false }) { Text(stringResource(R.string.cancel)) } },
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
private fun HomeHeader(today: LocalDate, locale: Locale, onOpenFilters: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DoitWordmark()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(horizontalAlignment = Alignment.End) {
            Text(today.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale)),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onOpenFilters) {
            Icon(painterResource(R.drawable.ic_filter), contentDescription = stringResource(R.string.saved_filters),
                tint = MaterialTheme.colorScheme.primary)
        }
        }
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
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
    swipeDown: Long?,
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
                val downName = labels.firstOrNull { it.id == swipeDown }?.title
                    ?: stringResource(R.string.context_not_configured)
                TextButton(onClick = { onChoose(3) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.context_swipe_down, downName))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun ContextLabelPicker(labels: List<com.marinecircuits.doit.data.local.LabelEntity>, selected: Long?,
                               title: String, noneText: String, onSelect: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item { TextButton(onClick = { onSelect(null) }) { Text(noneText) } }
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
    onScheduleKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
    hint: String,
    belowStatusBar: Boolean,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSearchKey: (KeyEvent) -> Boolean,
    searchMode: Boolean,
    hasSearchResult: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    onSignOut: () -> Unit,
    onRecent: () -> Unit,
    onConfigureContext: () -> Unit,
    onConfigureNextActions: () -> Unit,
    showNextActionsOption: Boolean,
    showContextOption: Boolean,
    onProjectColor: (() -> Unit)?,
    onProjectSettings: (() -> Unit)?,
    onCreateSubproject: (() -> Unit)?,
    onArchiveProject: (() -> Unit)? = null,
    onDeleteProject: (() -> Unit)? = null,
    serverAddress: String?,
    status: List<StatusPart>,
    syncedOnce: Boolean,
    tokenRejected: Boolean,
    syncError: String?,
    focusRequester: FocusRequester,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var releaseNotesOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (belowStatusBar) Modifier.statusBarsPadding() else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (searchMode) ImeAction.Search else ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }, onSearch = { onSend() }),
            trailingIcon = {
                FilledIconButton(onClick = onSend, enabled = if (searchMode) hasSearchResult else text.text.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(end = 4.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )) {
                    Icon(painterResource(if (searchMode) R.drawable.ic_arrow_up_right else R.drawable.ic_add), contentDescription = stringResource(if (searchMode) R.string.search_open else R.string.capture_send))
                }
            },
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent(onSearchKey)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent(onScheduleKey)
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
                if (showNextActionsOption) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.next_actions_label)) },
                        onClick = { menuOpen = false; onConfigureNextActions() },
                    )
                }
                if (showContextOption) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_context_label)) },
                        onClick = { menuOpen = false; onConfigureContext() },
                    )
                }
                if (onCreateSubproject != null) DropdownMenuItem(
                    text = { Text(stringResource(R.string.create_subproject)) },
                    onClick = { menuOpen = false; onCreateSubproject() },
                )
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
                    if (onDeleteProject != null) DropdownMenuItem(
                        text = { Text("Delete project…") },
                        onClick = { menuOpen = false; onDeleteProject() },
                    )
                    if (onArchiveProject != null) DropdownMenuItem(
                        text = { Text(stringResource(R.string.archive_project)) },
                        onClick = { menuOpen = false; onArchiveProject() },
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
                    TextButton(onClick = { infoOpen = false; releaseNotesOpen = true }) {
                        Text(stringResource(R.string.release_notes))
                    }
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
    if (releaseNotesOpen) {
        val notes = remember(context) {
            runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }.getOrDefault("")
        }
        AlertDialog(
            onDismissRequest = { releaseNotesOpen = false },
            title = { Text(stringResource(R.string.release_history)) },
            text = {
                Text(notes.ifEmpty { stringResource(R.string.release_notes_unavailable) },
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { releaseNotesOpen = false }) { Text(stringResource(R.string.ok)) } },
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
internal fun ProjectHeader(title: String, onBack: () -> Unit, onHome: () -> Unit) {
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
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(onClick = onHome) { Text(stringResource(R.string.home)) }
    }
}

/**
 * Project tiles use nearly neutral dark surfaces and colored icon accents, with lighter fills in light mode.
 * The grid and label shortcuts scroll together, independently of a project's task list.
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
        columns = GridCells.Adaptive(minSize = 152.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp + navigationBar),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        item(key = "projects-heading", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(if (largeTiles) R.string.your_projects else R.string.subprojects_heading),
                    style = MaterialTheme.typography.titleMedium)
                Text(tiles.size.toString(), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (homeLabels != null && tiles.isEmpty()) {
            item(key = "no-projects", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), Alignment.Center) {
                    Text(stringResource(R.string.no_projects), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(tiles, key = { it.project.id }) { tile ->
            val colors = projectTileColors(tile.project.hexColor)
            val accent = projectAccent(tile.project.hexColor)
            OutlinedCard(
                onClick = { onOpen(tile.project.id) },
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = colors?.let { Color(it.container) } ?: MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = colors?.let { Color(it.content) } ?: MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (largeTiles) 92.dp else 60.dp).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    if (largeTiles) {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Icon(painterResource(if (tile.project.id in shoppingProjects) R.drawable.ic_shopping_bag else R.drawable.ic_folder),
                                contentDescription = if (tile.project.id in shoppingProjects) stringResource(R.string.shopping_list) else null,
                                tint = accent,
                                modifier = Modifier.size(28.dp).background(accent.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(5.dp))
                            Icon(painterResource(R.drawable.ic_arrow_up_right), contentDescription = null,
                                tint = colors?.let { Color(it.content).copy(alpha = 0.55f) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        if (!largeTiles && tile.project.id in shoppingProjects) "🛒 ${tile.project.title}" else tile.project.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (homeLabels != null) {
            item(key = "labels-heading", span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.home_labels), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
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
            val colors = projectTileColors(label.hexColor)
            val accent = projectAccent(label.hexColor)
            Surface(
                onClick = { onOpen(label.id) },
                shape = RoundedCornerShape(14.dp),
                color = colors?.let { Color(it.container) } ?: MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = colors?.let { Color(it.content) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Box(Modifier.size(6.dp).background(accent, CircleShape))
                    Text("@${label.title}", style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun BulkMoveDialog(
    count: Int,
    projects: List<com.marinecircuits.doit.data.local.ProjectEntity>,
    onDismiss: () -> Unit,
    onMove: (Long) -> Unit,
) {
    var destination by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bulk_move_title, count)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(projects.filter { !it.archived && it.id > 0 }, key = { it.id }) { project ->
                    Row(
                        Modifier.fillMaxWidth().clickable { destination = project.id }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(project.title, Modifier.weight(1f))
                        if (destination == project.id) Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { destination?.let(onMove) }, enabled = destination != null) {
                Text(stringResource(R.string.bulk_move_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun StatusLine(parts: List<StatusPart>, modifier: Modifier = Modifier) {
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun TaskRowItem(
    row: TaskRow,
    dueLabel: String?,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit = {},
    selected: Boolean = false,
    selecting: Boolean = false,
    working: Boolean,
    onWork: (() -> Unit)?,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val repeat = Recurrence.Rule(row.task.repeatAfter, row.task.repeatMode)
    Column(Modifier.clip(MaterialTheme.shapes.medium)
        .background(if (selected || working) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
        .border(1.dp, if (working) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), MaterialTheme.shapes.medium)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(enabled = selecting || !row.completed, onClick = onOpen, onLongClick = onLongPress)
                .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .toggleable(value = if (selecting) selected else row.completed, role = Role.Checkbox,
                        onValueChange = { if (selecting) onOpen() else onCheckedChange(it) })
                    .semantics { contentDescription = row.task.title },
                contentAlignment = Alignment.Center,
            ) {
                if (if (selecting) selected else row.completed) {
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
                    Box(Modifier.size(24.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape))
                }
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    row.displayTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (row.completed) TextDecoration.LineThrough else null,
                    ),
                    color = if (row.completed) muted else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.description.isNotEmpty()) {
                    TaskDescriptionText(row.description, Modifier.padding(top = 2.dp))
                }
                row.projectTitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = muted)
                }
                if (row.quantity != null || repeat.active || dueLabel != null) {
                    FlowRow(modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.quantity?.let { quantity ->
                            Text(quantity, modifier = Modifier.widthIn(max = 140.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)).padding(6.dp), maxLines = 2,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        if (repeat.active || dueLabel != null) {
                            Row(
                                Modifier
                                    .background(if (row.overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                val scheduleColor = if (row.overdue) MaterialTheme.colorScheme.error else muted
                                if (repeat.active) {
                                    Icon(
                                        painterResource(R.drawable.ic_repeat),
                                        contentDescription = repeat.summary,
                                        tint = scheduleColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                dueLabel?.let {
                                    Text(it, style = MaterialTheme.typography.labelLarge, color = scheduleColor,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                if (row.labels.isNotEmpty()) {
                    LabelChips(row.labels, dimmed = row.completed, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (row.links.isNotEmpty()) TaskLinkIcons(row.links, Modifier.padding(start = 4.dp), stacked = true)
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
