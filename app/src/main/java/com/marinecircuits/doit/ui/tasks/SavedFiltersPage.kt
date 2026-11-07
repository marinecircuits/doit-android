package com.marinecircuits.doit.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.R

@Composable
fun SavedFiltersPage(
    state: SavedFiltersState,
    rows: List<TaskRow>,
    onSelect: (Long) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    taskContent: @Composable (TaskRow) -> Unit,
) {
    val selected = state.selected
    val hasItems = if (selected == null) state.filters.isNotEmpty() else rows.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        ProjectHeader(selected?.title ?: stringResource(R.string.saved_filters), onBack, onHome)
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (selected == null) R.string.saved_filters_hint else R.string.saved_filters),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh, enabled = !state.loading) {
                Text(stringResource(R.string.saved_filters_refresh))
            }
        }
        if (state.loading && hasItems) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { error ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(when (error) {
                        SavedFiltersError.CONNECTION -> R.string.saved_filters_connection
                        SavedFiltersError.PERMISSION -> R.string.saved_filters_permission
                        SavedFiltersError.REMOVED -> R.string.saved_filter_removed
                        SavedFiltersError.OTHER -> R.string.saved_filters_error
                    }), style = MaterialTheme.typography.bodyMedium)
                    if (hasItems) Text(stringResource(R.string.saved_filters_cached), style = MaterialTheme.typography.bodySmall)
                    if (error != SavedFiltersError.REMOVED) TextButton(onClick = onRefresh, enabled = !state.loading) {
                        Text(stringResource(R.string.saved_filters_retry))
                    }
                }
            }
        }
        if (state.loading && !hasItems) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { CircularProgressIndicator() }
        } else {
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selected == null) {
                    items(state.filters, key = { it.id }) { filter ->
                        OutlinedCard(onClick = { onSelect(filter.id) }, shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Icon(painterResource(R.drawable.ic_filter), contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(filter.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val description = TaskDescriptions.plain(filter.description)
                                    if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(painterResource(R.drawable.ic_arrow_up_right), contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                } else {
                    items(rows, key = { it.task.id }) { row -> taskContent(row) }
                }
                if (!hasItems && state.error == null) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(painterResource(R.drawable.ic_filter), contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Text(stringResource(if (selected == null) R.string.saved_filters_empty else R.string.saved_filter_empty),
                                style = MaterialTheme.typography.titleMedium)
                            if (selected == null) Text(stringResource(R.string.saved_filters_empty_hint),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
