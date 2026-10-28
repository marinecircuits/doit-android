package com.marinecircuits.doit.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.R

@Composable
internal fun CreateSubprojectDialog(
    draft: CreateSubprojectState,
    onTitleChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(draft.parent.id) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_subproject)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.subproject_parent, draft.parent.title))
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.project_name)) },
                    singleLine = true,
                    enabled = !draft.saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCreate() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                draft.error?.let { error ->
                    Text(
                        stringResource(when (error) {
                            SubprojectError.CONNECTION -> R.string.subproject_connection_error
                            SubprojectError.PERMISSION -> R.string.subproject_permission_error
                            SubprojectError.OTHER -> R.string.subproject_create_error
                        }),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate, enabled = !draft.saving && draft.title.isNotBlank()) {
                Text(stringResource(if (draft.saving) R.string.subproject_creating else R.string.create_subproject))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !draft.saving) { Text(stringResource(R.string.cancel)) }
        },
    )
}
