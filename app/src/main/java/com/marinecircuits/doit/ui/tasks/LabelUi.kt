package com.marinecircuits.doit.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.data.CaptureSyntax.Suggestion

/** A task's labels as small chips, in their colours made lighter like project tiles, or plain without one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelChips(labels: List<LabelChip>, dimmed: Boolean, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.then(if (dimmed) Modifier.alpha(0.6f) else Modifier),
    ) {
        labels.forEach { label ->
            val colors = ProjectColors.tile(label.hexColor)
            Surface(
                shape = CircleShape,
                color = colors?.let { Color(it.container) } ?: MaterialTheme.colorScheme.surfaceVariant,
                contentColor = colors?.let { Color(it.content) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    label.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Labels or projects matching a capture token. Nothing in it takes focus, so the field keeps the keyboard.
 */
@Composable
fun CaptureSuggestions(suggestions: List<Suggestion>, onPick: (Suggestion) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        // Capped so the open keyboard still leaves a list, and scrollable so every match is reachable.
        Column(
            Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onPick(suggestion) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SuggestionDot(suggestion.hexColor)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        suggestion.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionDot(hexColor: String) {
    val colors = ProjectColors.tile(hexColor)
    val dot = Modifier.size(12.dp)
    if (colors != null) {
        Box(dot.background(Color(colors.container), CircleShape))
    } else {
        Box(dot.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape))
    }
}
