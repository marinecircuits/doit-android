package com.marinecircuits.doit.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.R

@Composable
fun SearchResults(query: String, results: List<TaskSearch.Result>, selected: Int, onOpen: (TaskSearch.Result) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(query, selected) {
        if (selected in results.indices) listState.animateScrollToItem(selected)
    }
    when {
        query.isBlank() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.search_hint)) }
        results.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.search_empty)) }
        else -> LazyColumn(Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(results, key = { _, result -> "${result.kind}:${result.id}" }) { index, result ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (index == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(result) },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (result.kind) {
                                TaskSearch.Kind.PROJECT -> stringResource(R.string.search_project)
                                TaskSearch.Kind.TASK -> stringResource(R.string.search_task)
                                TaskSearch.Kind.LABEL -> stringResource(R.string.search_label)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(result.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (result.context.isNotBlank()) Text(result.context, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
