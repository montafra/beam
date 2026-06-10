package montafra.beam.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import montafra.beam.R

data class ChangelogEntry(val versionCode: Int, val versionName: String, val body: String)

fun loadChangelogs(context: Context, sinceVersionCode: Int, currentVersionCode: Int): List<ChangelogEntry> {
    val assetMgr = context.assets
    val locale = context.resources.configuration.locales[0].language
    val lang = listOf(locale, "en").firstOrNull { tag ->
        runCatching { assetMgr.list("changelogs/$tag") }.getOrNull()?.isNotEmpty() == true
    } ?: return emptyList()
    val files = assetMgr.list("changelogs/$lang") ?: return emptyList()
    return files
        .mapNotNull { name ->
            val code = name.removeSuffix(".txt").toIntOrNull() ?: return@mapNotNull null
            if (code <= sinceVersionCode || code > currentVersionCode) return@mapNotNull null
            val raw = runCatching {
                assetMgr.open("changelogs/$lang/$name").bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            val lines = raw.lines()
            ChangelogEntry(
                versionCode = code,
                versionName = lines.firstOrNull()?.trim().orEmpty(),
                body = lines.drop(1).joinToString("\n").trim(),
            )
        }
        .sortedByDescending { it.versionCode }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet(entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxPull = with(density) { 150.dp.toPx() }
    val threshold = with(density) { 90.dp.toPx() }
    // Easter egg: overscroll past the bottom of the changelog to peek a shrug while
    // pulling; it springs back to hidden on release.
    val pull = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val pullConn = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // At the bottom, leftover upward drag (available.y < 0) becomes rubber-band pull.
                if (source == NestedScrollSource.UserInput && available.y < 0f) {
                    val next = (pull.value - available.y * 0.5f).coerceIn(0f, maxPull)
                    scope.launch { pull.snapTo(next) }
                    if (!armed && next >= threshold) {
                        armed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Release: hide the shrug again by springing the pull back to zero.
                armed = false
                scope.launch { pull.animateTo(0f, spring()) }
                return Velocity.Zero
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(pullConn)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ico_info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.whatsNew),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            entries.forEachIndexed { index, entry ->
                if (index == 0) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (entry.versionName.isNotEmpty()) {
                                Text(
                                    text = "Beam ${entry.versionName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                text = entry.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    if (entry.versionName.isNotEmpty()) {
                        Text(
                            text = "Beam ${entry.versionName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        text = entry.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp)
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    text = "¯\\_(ツ)_/¯",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val reveal = (pull.value / threshold).coerceIn(0f, 1f)
                            alpha = reveal
                            translationY = (1f - reveal) * 12.dp.toPx()
                        },
                )
            }
        }
    }
}
