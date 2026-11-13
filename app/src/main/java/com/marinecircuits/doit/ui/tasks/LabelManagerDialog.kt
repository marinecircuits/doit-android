package com.marinecircuits.doit.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.remote.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LabelManagerDialog(labels: List<LabelEntity>, onSave: suspend (Long?, String, String) -> Unit,
                                onDelete: suspend (Long) -> Unit, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var id by remember { mutableStateOf<Long?>(null) }
    var title by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun act(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { operation(); editing = false; confirmDelete = false }
            catch (e: CancellationException) { throw e }
            catch (e: ApiException) { error = if (e.status == 401 || e.status == 403) "Check label permissions on your API token and account." else "Server rejected the change. Refresh and try again." }
            catch (e: java.io.IOException) { error = "Could not reach the server. Label management needs a connection." }
            catch (e: IllegalArgumentException) { error = e.message }
            catch (e: IllegalStateException) { error = e.message }
            finally { busy = false }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (!editing) "Manage labels" else if (id == null) "Create label" else "Edit label") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!editing) {
                    Text("Changes apply to labels across all projects. Requires a connection.")
                    TextButton(enabled = !busy, onClick = { id = null; title = ""; color = ""; editing = true; error = null }) { Text("Create label…") }
                    labels.forEach { label ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                            id = label.id; title = label.title; color = label.hexColor; editing = true; error = null
                        }.padding(vertical = 8.dp)) { LabelChips(listOf(LabelChip(label.title, label.hexColor)), false) }
                    }
                } else {
                    OutlinedTextField(title, { title = it }, label = { Text("Name") }, enabled = !busy, singleLine = true)
                    OutlinedTextField(color, { color = it }, label = { Text("Colour (hex, optional)") }, enabled = !busy, singleLine = true)
                    if (id != null) TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Delete label…") }
                }
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (editing) TextButton(enabled = !busy && title.isNotBlank(), onClick = { act { onSave(id, title, color) } }) { Text("Save") }
            else TextButton(enabled = !busy, onClick = onDismiss) { Text("Done") }
        },
        dismissButton = { if (editing) TextButton(enabled = !busy, onClick = { editing = false; error = null }) { Text("Cancel") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text("Delete label?") },
        text = { Text("Delete “${labels.firstOrNull { it.id == id }?.title.orEmpty()}” from all tasks? Tasks themselves will not be deleted.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val target = id ?: return@TextButton
            confirmDelete = false
            act { onDelete(target) }
        }) { Text("Delete") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
