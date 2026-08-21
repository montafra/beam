package montafra.beam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Gap left between the status bar and the top edge of a fully expanded sheet. */
private val SheetTopGap = 16.dp

/**
 * The stock Material3 pill, made tappable: a tap snaps the sheet to its top position, and a second
 * tap collapses it back to the half detent on sheets that have one.
 *
 * Haptics go through `LocalHapticFeedback`, which MainActivity overrides with a [TapFeedback], so
 * the tap also plays the click sound and is gated by the haptics/sound preferences for free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeamDragHandle(sheetState: SheetState) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            // No indication: the pill draws nothing on press, the haptic and click sound are the
            // whole feedback.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    // partialExpand() on a skipPartiallyExpanded sheet has no anchor to land on, so
                    // guard it — otherwise currentValue desyncs from where the sheet actually sits.
                    if (sheetState.currentValue == SheetValue.Expanded && sheetState.hasPartiallyExpandedState) {
                        sheetState.partialExpand()
                    } else {
                        sheetState.expand()
                    }
                }
            }
            // After clickable, so the padding widens the tap target instead of shrinking it.
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle()
    }
}

/**
 * Shared bottom sheet for the whole app: tappable drag handle, scrollable body, and a body height
 * capped so a tall sheet stops [SheetTopGap] below the status bar instead of running to the top of
 * the window.
 *
 * The cap is read off the measured constraints rather than `LocalConfiguration.screenHeightDp`,
 * whose meaning changed on API 35 for edge-to-edge apps. Capping the body is what bounds the sheet,
 * so short sheets still wrap their content and are untouched.
 *
 * [contentModifier] is inserted before the scroll modifier, for call sites that need to observe the
 * body's nested scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeamSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BeamDragHandle(sheetState) },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight - statusBar - SheetTopGap)
                    .then(contentModifier)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}
