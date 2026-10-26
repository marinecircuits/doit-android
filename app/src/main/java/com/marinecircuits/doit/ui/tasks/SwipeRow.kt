package com.marinecircuits.doit.ui.tasks

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.marinecircuits.doit.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Fraction of the row width a swipe must pass to count; shorter swipes snap back. */
private const val SWIPE_THRESHOLD = 0.35f

/**
 * A row that can be swiped right ([onSwipedRight], complete) or left ([onSwipedLeft], reschedule).
 * Built on plain drag gestures because Material's swipe-to-dismiss API keeps changing shape between releases.
 */
@Composable
fun SwipeRow(
    onSwipedRight: () -> Unit,
    onSwipedLeft: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        val x = offset.value
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(
                    when {
                        x > 0 -> MaterialTheme.colorScheme.primaryContainer
                        x < 0 -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> Color.Transparent
                    },
                )
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (x > 0) {
                Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.weight(1f))
            if (x < 0) {
                Icon(painterResource(R.drawable.ic_event), null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val value = offset.value
                            val threshold = width * SWIPE_THRESHOLD
                            armed = false
                            scope.launch {
                                when {
                                    value > threshold -> {
                                        offset.animateTo(width.toFloat())
                                        onSwipedRight()
                                        offset.snapTo(0f)
                                    }
                                    value < -threshold -> {
                                        offset.animateTo(0f)
                                        onSwipedLeft()
                                    }
                                    else -> offset.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = {
                            armed = false
                            scope.launch { offset.animateTo(0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val next = offset.value + dragAmount
                            scope.launch { offset.snapTo(next) }
                            val nowArmed = abs(next) > width * SWIPE_THRESHOLD
                            if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            armed = nowArmed
                        },
                    )
                },
        ) {
            content()
        }
    }
}
