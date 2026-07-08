package montafra.beam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * A settings toggle row that keeps the trailing [Switch] (and optional leading icon)
 * vertically centered, even when the description wraps to multiple lines.
 *
 * Material3's [androidx.compose.material3.ListItem] top-aligns its leading/trailing content
 * as soon as it treats the item as "three-line" — which happens the moment the supporting
 * text spans two lines — so the Switch drifts to the top on longer descriptions. This row
 * uses an explicit [Alignment.CenterVertically] instead. The whole row is clickable and
 * toggles the switch, matching the previous `ListItem` behaviour.
 *
 * @param onHaptic optional custom haptic; defaults to [HapticFeedbackType.TextHandleMove].
 */
@Composable
fun ToggleSettingRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: Painter? = null,
    onHaptic: ((Boolean) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val performHaptic: (Boolean) -> Unit =
        onHaptic ?: { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val toggle = { next: Boolean ->
        performHaptic(next)
        onCheckedChange(next)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { toggle(!checked) }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { toggle(it) },
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
