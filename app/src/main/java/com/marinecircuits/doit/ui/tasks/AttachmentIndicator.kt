package com.marinecircuits.doit.ui.tasks

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.R

@Composable
internal fun AttachmentIndicator() {
    Icon(painterResource(R.drawable.ic_attachment), contentDescription = "Has attachments", modifier = Modifier.size(20.dp))
}
