package com.marinecircuits.doit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

@Composable
fun DoitWordmark(modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("doit")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(".") }
        },
        modifier = modifier,
        style = MaterialTheme.typography.displaySmall,
    )
}
