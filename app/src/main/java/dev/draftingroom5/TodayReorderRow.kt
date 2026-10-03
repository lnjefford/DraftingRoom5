package dev.draftingroom5

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp

@Composable
internal fun TodayReorderRow(
    label: String,
    position: Int,
    total: Int,
    onMove: (Int) -> Boolean,
    trailing: @Composable (() -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    val currentMove by rememberUpdatedState(onMove)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .background(if (dragging) TodayCopper.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(10.dp))
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown || !it.isCtrlPressed) false
                else when (it.key) {
                    Key.DirectionUp -> onMove(-1)
                    Key.DirectionDown -> onMove(1)
                    else -> false
                }
            }
            .focusable()
            .semantics {
                stateDescription = "$label, position ${position + 1} of $total"
                customActions = buildList {
                    if (position > 0) add(CustomAccessibilityAction("Move $label earlier") { onMove(-1) })
                    if (position < total - 1) add(CustomAccessibilityAction("Move $label later") { onMove(1) })
                }
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        trailing?.invoke()
        Icon(Icons.Default.DragHandle,
            contentDescription = "Drag to reorder $label, position ${position + 1} of $total",
            tint = TodayLavender,
            modifier = Modifier.size(48.dp).pointerInput(label) {
                var distance = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        distance = 0f
                        dragging = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragCancel = { dragging = false },
                    onDragEnd = { dragging = false },
                ) { change, amount ->
                    change.consume()
                    distance += amount.y
                    while (distance <= -threshold) {
                        if (currentMove(-1)) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        distance += threshold
                    }
                    while (distance >= threshold) {
                        if (currentMove(1)) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        distance -= threshold
                    }
                }
            }.padding(10.dp))
    }
}
