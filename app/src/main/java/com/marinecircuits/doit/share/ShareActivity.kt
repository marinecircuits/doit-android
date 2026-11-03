package com.marinecircuits.doit.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import com.marinecircuits.doit.ui.tasks.DateTrigger
import com.marinecircuits.doit.ui.tasks.PickDateDialog
import com.marinecircuits.doit.ui.tasks.SyntaxKeyRow
import com.marinecircuits.doit.ui.tasks.SyntaxKeys
import com.marinecircuits.doit.ui.theme.DoitTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.time.LocalDate

/** The share-sheet target: a small dialog over the app the text came from. */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isPdf = intent.action == Intent.ACTION_SEND &&
            intent.type?.equals("application/pdf", ignoreCase = true) == true
        val pdfUri = if (isPdf) sharedUri(intent) else null
        val pdf = if (isPdf) {
            SharedPdf.fromDisplayName(pdfUri?.let(::displayName) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT))
        } else null
        val shared = if (pdf != null) {
            SharedTask(
                pdf.title,
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            )
        } else {
            SharedText.propose(
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            )
        }
        setContent {
            DoitTheme {
                ShareDialog(
                    shared = shared,
                    pdfName = pdf?.filename,
                    onAdd = { title, description, dueDate ->
                        if (pdf != null) {
                            val source = pdfUri ?: throw FileNotFoundException("The shared PDF has no URI")
                            // Read while the sending app's temporary URI permission is still valid.
                            val bytes = withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(source)?.use(SharedPdf::read)
                                    ?: throw FileNotFoundException("The shared PDF could not be opened")
                            }
                            checkNotNull(
                                container.repository.capturePdf(
                                    title, pdf.filename, bytes, description = description, dueDate = dueDate,
                                ),
                            ) { "The task needs a title" }
                        } else {
                            container.repository.capture(title, description, dueDate = dueDate)
                        }
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

    private fun sharedUri(intent: Intent): Uri? {
        @Suppress("DEPRECATION")
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        return stream ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun ShareDialog(
    shared: SharedTask,
    pdfName: String?,
    onAdd: suspend (title: String, description: String?, dueDate: LocalDate?) -> Unit,
    onOpenApp: () -> Unit,
    onCancel: () -> Unit,
) {
    val container = LocalContext.current.container
    val connected by produceState<Boolean?>(null) { value = container.settings.credentials.first() != null }
    val scope = rememberCoroutineScope()
    val failedMessage = stringResource(R.string.share_failed)
    val tooLargeMessage = stringResource(R.string.share_pdf_too_large)
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf(TextFieldValue(shared.title, TextRange(shared.title.length))) }
    var titleFocused by remember { mutableStateOf(false) }
    val labels by container.repository.labels.collectAsState(initial = emptyList())
    val projects by container.repository.projects.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

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
            onDismissRequest = { if (!adding) onCancel() },
            title = { Text(stringResource(R.string.share_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { value ->
                            val withoutTrigger = DateTrigger.consume(title, value)
                            title = withoutTrigger ?: value
                            if (withoutTrigger != null) pickingDate = true
                        },
                        label = { Text(stringResource(R.string.edit_title)) },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { titleFocused = it.isFocused },
                    )
                    if (titleFocused) {
                        SyntaxKeyRow(
                            onInsert = { token -> title = SyntaxKeys.insert(title, token) },
                            modifier = Modifier.fillMaxWidth(),
                            onPickDate = { pickingDate = true },
                        )
                    }
                    dueDate?.let { selected ->
                        androidx.compose.foundation.layout.Row {
                            TextButton(onClick = { pickingDate = true }) {
                                Text(stringResource(R.string.capture_due_date, selected.toString()))
                            }
                            TextButton(onClick = { dueDate = null }) { Text(stringResource(R.string.edit_clear_date)) }
                        }
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
                    pdfName?.let {
                        Text(
                            stringResource(R.string.share_pdf_name, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.text.isNotBlank() && !adding,
                    onClick = {
                        adding = true
                        error = null
                        scope.launch {
                            try {
                                onAdd(title.text, shared.description, dueDate)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: PdfTooLargeException) {
                                error = tooLargeMessage
                            } catch (_: Exception) {
                                error = failedMessage
                            } finally {
                                adding = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.share_add)) }
            },
            dismissButton = {
                TextButton(onClick = onCancel, enabled = !adding) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (connected == true && pickingDate) {
        PickDateDialog(
            initial = dueDate ?: LocalDate.now(),
            onPicked = { dueDate = it; pickingDate = false },
            onDismiss = { pickingDate = false },
        )
    }
}
