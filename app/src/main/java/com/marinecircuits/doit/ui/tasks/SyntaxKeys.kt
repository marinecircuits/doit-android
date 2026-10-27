package com.marinecircuits.doit.ui.tasks

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.R

/**
 * `@` and `#` keys for task text. They are not focusable, so tapping one leaves the field and its
 * keyboard open. A space is added on either side when the surrounding character would otherwise
 * swallow the token or glue it to the neighbouring word.
 */
object SyntaxKeys {
    fun insert(value: TextFieldValue, token: String): TextFieldValue {
        val (text, cursor) = insert(value.text, value.selection.start, value.selection.end, token)
        return TextFieldValue(text, TextRange(cursor))
    }

    fun insert(text: String, selectionStart: Int, selectionEnd: Int, token: String): Pair<String, Int> {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val prefix = if (start > 0 && !text[start - 1].isWhitespace()) " " else ""
        val suffix = if (end < text.length && !text[end].isWhitespace()) " " else ""
        return text.replaceRange(start, end, prefix + token + suffix) to start + prefix.length + token.length
    }
}

@Composable
fun SyntaxKeyRow(onInsert: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SyntaxKey("@", stringResource(R.string.insert_label), onInsert)
        SyntaxKey("#", stringResource(R.string.insert_project), onInsert)
    }
}

@Composable
private fun SyntaxKey(token: String, description: String, onInsert: (String) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(description) {
                    onInsert(token)
                    true
                }
            }
            .pointerInput(token) { detectTapGestures(onTap = { onInsert(token) }) },
    ) {
        Box(
            Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 48.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(token, style = MaterialTheme.typography.titleLarge)
        }
    }
}
