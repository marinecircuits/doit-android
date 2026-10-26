package com.marinecircuits.doit.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.R

@Composable
fun SetupScreen(viewModel: SetupViewModel) {
    val reconnecting = viewModel.fixedApiBase != null
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (reconnecting) R.string.setup_reconnect_title else R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (reconnecting) Text(stringResource(R.string.setup_reconnect_body))

            OutlinedTextField(
                value = viewModel.url,
                onValueChange = { viewModel.url = it },
                enabled = !reconnecting,
                label = { Text(stringResource(R.string.setup_server)) },
                placeholder = { Text(stringResource(R.string.setup_server_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.token,
                onValueChange = { viewModel.token = it },
                label = { Text(stringResource(R.string.setup_token)) },
                placeholder = { Text(stringResource(R.string.setup_token_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.connect() }),
                modifier = Modifier.fillMaxWidth(),
            )

            viewModel.error?.let { error ->
                Text(errorText(error), color = MaterialTheme.colorScheme.error)
            }

            Button(onClick = viewModel::connect, enabled = !viewModel.busy, modifier = Modifier.fillMaxWidth()) {
                if (viewModel.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.setup_connect))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.setup_permissions_title), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.setup_permissions), style = MaterialTheme.typography.bodyMedium)
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
