package com.marinecircuits.doit.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.MainActivity
import com.marinecircuits.doit.R
import com.marinecircuits.doit.container
import com.marinecircuits.doit.data.CaptureSyntax
import com.marinecircuits.doit.ui.tasks.CaptureSuggestions
import com.marinecircuits.doit.ui.tasks.SyntaxKeyRow
import com.marinecircuits.doit.ui.tasks.SyntaxKeys
import com.marinecircuits.doit.ui.theme.DoitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The share-sheet target: a small dialog over the app the text came from. */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = SharedText.propose(
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
        )
        setContent {
            DoitTheme {
                ShareDialog(
                    shared = shared,
                    onAdd = { title, description ->
                        container.repository.capture(title, description)
                        finish()
                    },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onCancel = ::finish,
                )
            }
        }
    }
}

@Composable
private fun ShareDialog(
    shared: SharedTask,
    onAdd: suspend (title: String, description: String?) -> Unit,
    onOpenApp: () -> Unit,
    onCancel: () -> Unit,
) {
    val container = LocalContext.current.container
    val connected by produceState<Boolean?>(null) { value = container.settings.credentials.first() != null }
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(TextFieldValue(shared.title, TextRange(shared.title.length))) }
    var titleFocused by remember { mutableStateOf(false) }
    val labels by container.repository.labels.collectAsState(initial = emptyList())
    val projects by container.repository.projects.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }

    when (connected) {
        null -> Unit
        false -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.share_title)) },
            text = { Text(stringResource(R.string.share_not_connected)) },
            confirmButton = { TextButton(onClick = onOpenApp) { Text(stringResource(R.string.share_open_app)) } },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
        )
        true -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.share_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.edit_title)) },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { titleFocused = it.isFocused },
                    )
                    if (titleFocused) {
                        SyntaxKeyRow(
                            onInsert = { token -> title = SyntaxKeys.insert(title, token) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val suggestions = if (titleFocused && title.selection.collapsed) {
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
                    shared.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.text.isNotBlank() && !adding,
                    onClick = {
                        adding = true
                        scope.launch { onAdd(title.text, shared.description) }
                    },
                ) { Text(stringResource(R.string.share_add)) }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
