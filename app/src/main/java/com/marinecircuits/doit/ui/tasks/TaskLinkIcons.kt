package com.marinecircuits.doit.ui.tasks

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.R

/** Description words. They are ordinary text: a web address in them is not tappable. */
@Composable
fun TaskDescriptionText(text: String, modifier: Modifier = Modifier, maxLines: Int = 4) {
    if (text.isBlank()) return
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** One globe for each address. [stacked] puts them down the side of a task row. */
@Composable
fun TaskLinkIcons(links: List<String>, modifier: Modifier = Modifier, stacked: Boolean = false) {
    if (links.isEmpty()) return
    val context = LocalContext.current
    val icons: @Composable () -> Unit = {
        links.forEach { url ->
            IconButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: ActivityNotFoundException) {
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_public),
                    contentDescription = stringResource(R.string.open_link),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (stacked) Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, content = { icons() })
    else Row(modifier, verticalAlignment = Alignment.CenterVertically, content = { icons() })
}
