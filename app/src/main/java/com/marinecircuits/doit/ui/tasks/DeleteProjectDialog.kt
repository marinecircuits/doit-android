package com.marinecircuits.doit.ui.tasks

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.marinecircuits.doit.data.ProjectDeletion
import com.marinecircuits.doit.data.sync.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DeleteProjectDialog(id: Long, engine: SyncEngine, onDeleted: () -> Unit, onDismiss: () -> Unit) {
    var preview by remember(id) { mutableStateOf<ProjectDeletion.Preview?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var busy by remember(id) { mutableStateOf(true) }
    var finalConfirmation by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        busy = true
        error = null
        try { preview = engine.previewProjectDeletion(id) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Could not read project contents" }
        finally { busy = false }
    }
    LaunchedEffect(id) { load() }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (finalConfirmation) "Permanently delete project?" else "Delete project") },
        text = {
            Text(buildString {
                if (busy) append("Contacting the server…\n\n")
                preview?.let { append("${it.title}\n${it.counts}\n\nThis permanently deletes the project, its subprojects, and their tasks. This cannot be undone.\n\n") }
                error?.let { append("$it\n\n") }
                append("Requires a connection, project administrator access, and permission to read projects and tasks and delete projects. If a request fails, sync before retrying.")
            })
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val selected = preview
                when {
                    selected == null -> scope.launch { load() }
                    selected.nonempty && !finalConfirmation -> finalConfirmation = true
                    else -> scope.launch {
                        busy = true
                        try { engine.deleteProject(selected); onDeleted() }
                        catch (e: CancellationException) { throw e }
                        catch (e: ProjectDeletion.Changed) { preview = e.preview; error = e.message; finalConfirmation = false }
                        catch (e: Exception) { preview = null; error = e.message ?: "Deletion failed; sync before retrying"; finalConfirmation = false }
                        finally { busy = false }
                    }
                }
            }) { Text(if (preview == null) "Retry" else if (preview!!.nonempty && !finalConfirmation) "Continue" else "Delete permanently") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}
