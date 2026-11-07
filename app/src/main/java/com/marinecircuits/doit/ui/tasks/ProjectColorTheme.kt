package com.marinecircuits.doit.ui.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
internal fun projectTileColors(hexColor: String): TileColors? =
    ProjectColors.tile(hexColor, dark = MaterialTheme.colorScheme.background.luminance() < 0.5f)

@Composable
internal fun projectAccent(hexColor: String): Color =
    ProjectColors.accent(hexColor, dark = MaterialTheme.colorScheme.background.luminance() < 0.5f)
        ?.let(::Color) ?: MaterialTheme.colorScheme.primary
