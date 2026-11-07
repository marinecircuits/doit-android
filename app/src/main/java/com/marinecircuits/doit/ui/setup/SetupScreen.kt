package com.marinecircuits.doit.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.R
import com.marinecircuits.doit.ui.theme.DoitWordmark

@Composable
fun SetupScreen(viewModel: SetupViewModel) {
    val reconnecting = viewModel.fixedApiBase != null
    var instructionsOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel.error) {
        if (viewModel.error == SetupError.MissingPermission) instructionsOpen = true
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    )
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.TopCenter).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                DoitWordmark()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setup_tagline), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(if (reconnecting) R.string.setup_reconnect_title else R.string.setup_title),
                            style = MaterialTheme.typography.titleLarge)
                        if (reconnecting) Text(stringResource(R.string.setup_reconnect_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = viewModel.url,
                            onValueChange = { viewModel.url = it },
                            enabled = !reconnecting,
                            label = { Text(stringResource(R.string.setup_server)) },
                            placeholder = { Text(stringResource(R.string.setup_server_hint)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Keep tokens plain and pasteable; password managers and autocorrect must not alter them.
                        OutlinedTextField(
                            value = viewModel.token,
                            onValueChange = { viewModel.token = it },
                            label = { Text(stringResource(R.string.setup_token)) },
                            placeholder = { Text(stringResource(R.string.setup_token_hint)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { viewModel.connect() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        viewModel.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                        Button(onClick = viewModel::connect, enabled = !viewModel.busy,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            if (viewModel.busy) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.setup_connect), style = MaterialTheme.typography.titleSmall)
                                    Icon(painterResource(R.drawable.ic_arrow_up_right), contentDescription = null,
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                Column {
                    TextButton(onClick = { instructionsOpen = !instructionsOpen }) {
                        Text(stringResource(if (instructionsOpen) R.string.setup_hide_token_help else R.string.setup_token_help))
                    }
                    AnimatedVisibility(instructionsOpen) {
                        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.setup_permissions_title), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.setup_permissions), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun errorText(error: SetupError): String = when (error) {
    SetupError.BlankUrl -> stringResource(R.string.setup_error_blank_url)
    SetupError.InsecureHttp -> stringResource(R.string.setup_error_http)
    SetupError.InvalidUrl -> stringResource(R.string.setup_error_invalid_url)
    SetupError.BlankToken -> stringResource(R.string.setup_error_blank_token)
    SetupError.Unreachable -> stringResource(R.string.setup_error_unreachable)
    SetupError.TokenRejected -> stringResource(R.string.setup_error_token_rejected)
    SetupError.MissingPermission -> stringResource(R.string.setup_error_missing_permission)
    is SetupError.Other -> stringResource(R.string.setup_error_other, error.message)
}
