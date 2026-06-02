package montafra.beam.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import montafra.beam.BatteryViewModel
import montafra.beam.R
import montafra.beam.settingsName
import montafra.beam.settingsUpdateInd
import montafra.beam.ui.theme.BeamCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkaroundsSettingsScreen(navController: NavController, vm: BatteryViewModel = viewModel()) {
    val data by vm.data.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    val pollIntervals = listOf(1_250L, 1_750L, 2_500L, 3_500L, 5_000L, 7_500L, 10_000L)
    val pollLabels = listOf("1.25s", "1.75s", "2.5s", "3.5s", "5s", "7.5s", "10s")

    var currentScalar by remember { mutableFloatStateOf(prefs.getFloat("currentScalar", 1f)) }
    var invertCurrent by remember { mutableStateOf(prefs.getBoolean("invertCurrent", false)) }
    var pollIndex by remember {
        mutableIntStateOf(pollIntervals.indexOf(prefs.getLong("pollIntervalMs", 1_750L)).coerceAtLeast(0))
    }

    fun saveWorkarounds() {
        prefs.edit()
            .putFloat("currentScalar", currentScalar)
            .putBoolean("invertCurrent", invertCurrent)
            .putLong("pollIntervalMs", pollIntervals[pollIndex])
            .commit()
        context.sendBroadcast(
            Intent().setPackage(context.packageName).setAction(settingsUpdateInd)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workarounds)) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        navController.popBackStack()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ico_back),
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                Text(
                    text = stringResource(R.string.workaroundsDesc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SubLabel(stringResource(R.string.currentScalar))
                Spacer(Modifier.height(8.dp))
                val scalarOptions = remember { listOf("0.001×", "1×", "1000×") }
                val scalarValues = remember { listOf(0.001f, 1f, 1000f) }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    scalarOptions.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = currentScalar == scalarValues[i],
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentScalar = scalarValues[i]
                                saveWorkarounds()
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, scalarOptions.size),
                            label = { Text(label) },
                        )
                    }
                }
            }
            item {
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.invertCurrent)) },
                            supportingContent = { Text(stringResource(R.string.invertCurrentDesc)) },
                            trailingContent = {
                                Switch(
                                    checked = invertCurrent,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        invertCurrent = it
                                        saveWorkarounds()
                                    },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                invertCurrent = !invertCurrent
                                saveWorkarounds()
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.charging), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(data.charging, style = MaterialTheme.typography.bodyLarge)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.power), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(data.power, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.tweaks)) }
            item {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SubLabel(stringResource(R.string.pollInterval))
                        Text(
                            text = pollLabels[pollIndex],
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = pollIndex.toFloat(),
                        onValueChange = {
                            val new = it.roundToInt()
                            if (new != pollIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                pollIndex = new
                            }
                        },
                        onValueChangeFinished = { saveWorkarounds() },
                        valueRange = 0f..6f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Layout(
                        content = {
                            pollLabels.forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { measurables, constraints ->
                        val placeables = measurables.map { it.measure(Constraints()) }
                        val width = constraints.maxWidth
                        val height = placeables.maxOf { it.height }
                        val n = placeables.size
                        layout(width, height) {
                            placeables.forEachIndexed { i, placeable ->
                                val center = (width * i.toFloat() / (n - 1)).roundToInt()
                                val x = (center - placeable.width / 2)
                                    .coerceIn(0, width - placeable.width)
                                placeable.placeRelative(x, 0)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
