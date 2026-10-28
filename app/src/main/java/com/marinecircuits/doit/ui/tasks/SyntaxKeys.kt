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
        if (start == end) {
            // A bare @ or # with no name yet is replaced, so switching keys does not leave "@ # @".
            emptyMarkerAt(text, start)?.let { marker ->
                return if (text[marker] == token.singleOrNull()) {
                    text to start
                } else {
                    text.replaceRange(marker, start, token) to marker + token.length
                }
            }
        }
        val prefix = if (start > 0 && !text[start - 1].isWhitespace()) " " else ""
        val suffix = if (end < text.length && !text[end].isWhitespace()) " " else ""
        return text.replaceRange(start, end, prefix + token + suffix) to start + prefix.length + token.length
    }

    /** Index of an `@` or `#` that ends at [cursor] and has no name characters yet, if the cursor is in one. */
    private fun emptyMarkerAt(text: String, cursor: Int): Int? {
        if (cursor == 0 || cursor > text.length) return null
        val marker = cursor - 1
        if (text[marker] != '@' && text[marker] != '#') return null
        val atBoundary = marker == 0 || text[marker - 1].isWhitespace()
        val nameNotStarted = cursor == text.length || text[cursor].isWhitespace()
        return marker.takeIf { atBoundary && nameNotStarted }
    }
}

/** A typed `!` at a word boundary opens the date picker; pasted text and punctuation stay literal. */
object DateTrigger {
    fun consume(previous: TextFieldValue, next: TextFieldValue): TextFieldValue? {
        if (!previous.selection.collapsed || !next.selection.collapsed) return null
        val cursor = previous.selection.start
        if (cursor !in 0..previous.text.length || next.selection.start != cursor + 1) return null
        if (cursor > 0 && !previous.text[cursor - 1].isWhitespace()) return null
        if (previous.text.take(cursor).count { it == '"' } % 2 != 0) return null
        if (next.text != previous.text.replaceRange(cursor, cursor, "!")) return null
        return TextFieldValue(previous.text, TextRange(cursor))
    }
}

@Composable
fun SyntaxKeyRow(onInsert: (String) -> Unit, modifier: Modifier = Modifier, onPickDate: () -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SyntaxKey("@", stringResource(R.string.insert_label), onInsert)
        SyntaxKey("#", stringResource(R.string.insert_project), onInsert)
        SyntaxKey("!", stringResource(R.string.insert_date)) { onPickDate() }
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
