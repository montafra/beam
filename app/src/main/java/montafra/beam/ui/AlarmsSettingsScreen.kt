package montafra.beam.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import montafra.beam.R
import montafra.beam.settingsName
import montafra.beam.settingsUpdateInd
import montafra.beam.ui.theme.BeamCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsSettingsScreen(navController: BeamNavController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    var lowEnabled by remember { mutableStateOf(prefs.getBoolean("alarmLowEnabled", false)) }
    var lowThreshold by remember { mutableIntStateOf(prefs.getInt("alarmLowThreshold", 20)) }
    var lowRepeat by remember { mutableStateOf(prefs.getBoolean("alarmLowRepeat", false)) }

    var highEnabled by remember { mutableStateOf(prefs.getBoolean("alarmHighEnabled", false)) }
    var highThreshold by remember { mutableIntStateOf(prefs.getInt("alarmHighThreshold", 85)) }
    var highRepeat by remember { mutableStateOf(prefs.getBoolean("alarmHighRepeat", false)) }

    var tempEnabled by remember { mutableStateOf(prefs.getBoolean("alarmTempEnabled", false)) }
    var tempThreshold by remember { mutableIntStateOf(prefs.getInt("alarmTempThreshold", 40)) }
    var tempRepeat by remember { mutableStateOf(prefs.getBoolean("alarmTempRepeat", false)) }

    val repeatOptions = remember { listOf(5, 15, 30, 60) }
    val repeatLabels = remember { listOf("5m", "15m", "30m", "60m") }
    var repeatIndex by remember {
        mutableIntStateOf(repeatOptions.indexOf(prefs.getInt("alarmRepeatIntervalMin", 15)).coerceAtLeast(0))
    }

    fun saveAlarms() {
        prefs.edit()
            .putBoolean("alarmLowEnabled", lowEnabled)
            .putInt("alarmLowThreshold", lowThreshold)
            .putBoolean("alarmLowRepeat", lowRepeat)
            .putBoolean("alarmHighEnabled", highEnabled)
            .putInt("alarmHighThreshold", highThreshold)
            .putBoolean("alarmHighRepeat", highRepeat)
            .putBoolean("alarmTempEnabled", tempEnabled)
            .putInt("alarmTempThreshold", tempThreshold)
            .putBoolean("alarmTempRepeat", tempRepeat)
            .putInt("alarmRepeatIntervalMin", repeatOptions[repeatIndex])
            .commit()
        context.sendBroadcast(
            Intent().setPackage(context.packageName).setAction(settingsUpdateInd)
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.alarms),
        onBack = { navController.popBackStack() },
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Text(
                text = stringResource(R.string.alarmsDesc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            AlarmCard(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                title = stringResource(R.string.alarmLow),
                description = stringResource(R.string.alarmLowDesc),
                enabled = lowEnabled,
                onEnabledChange = { lowEnabled = it; saveAlarms() },
                valueLabel = "$lowThreshold%",
                sliderValue = lowThreshold.toFloat(),
                valueRange = 5f..50f,
                steps = 44,
                onSliderChange = { lowThreshold = it.roundToInt() },
                onSliderChangeFinished = { saveAlarms() },
                repeat = lowRepeat,
                onRepeatChange = { lowRepeat = it; saveAlarms() },
                haptic = haptic,
            )
            Spacer(Modifier.height(4.dp))
            AlarmCard(
                shape = RoundedCornerShape(4.dp),
                title = stringResource(R.string.alarmHigh),
                description = stringResource(R.string.alarmHighDesc),
                enabled = highEnabled,
                onEnabledChange = { highEnabled = it; saveAlarms() },
                valueLabel = "$highThreshold%",
                sliderValue = highThreshold.toFloat(),
                valueRange = 50f..100f,
                steps = 49,
                onSliderChange = { highThreshold = it.roundToInt() },
                onSliderChangeFinished = { saveAlarms() },
                repeat = highRepeat,
                onRepeatChange = { highRepeat = it; saveAlarms() },
                haptic = haptic,
            )
            Spacer(Modifier.height(4.dp))
            AlarmCard(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                title = stringResource(R.string.alarmTemp),
                description = stringResource(R.string.alarmTempDesc),
                enabled = tempEnabled,
                onEnabledChange = { tempEnabled = it; saveAlarms() },
                valueLabel = "$tempThreshold°C",
                sliderValue = tempThreshold.toFloat(),
                valueRange = 35f..55f,
                steps = 19,
                onSliderChange = { tempThreshold = it.roundToInt() },
                onSliderChangeFinished = { saveAlarms() },
                repeat = tempRepeat,
                onRepeatChange = { tempRepeat = it; saveAlarms() },
                haptic = haptic,
            )
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                SubLabel(stringResource(R.string.alarmRepeatInterval))
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    repeatLabels.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = repeatIndex == i,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                repeatIndex = i
                                saveAlarms()
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, repeatLabels.size),
                            label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AlarmCard(
    shape: Shape,
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    valueLabel: String,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    repeat: Boolean,
    onRepeatChange: (Boolean) -> Unit,
    haptic: HapticFeedback,
) {
    BeamCard(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
    ) {
        Column {
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = { Text(description) },
                trailingContent = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onEnabledChange(it)
                        },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onEnabledChange(!enabled)
                },
            )
            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(180)),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SubLabel(stringResource(R.string.alarmThreshold))
                        Text(
                            text = valueLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            if (it.roundToInt() != sliderValue.roundToInt()) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onSliderChange(it)
                        },
                        onValueChangeFinished = onSliderChangeFinished,
                        valueRange = valueRange,
                        steps = steps,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRepeatChange(!repeat)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = stringResource(R.string.alarmRepeat),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.alarmRepeatDesc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = repeat,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRepeatChange(it)
                            },
                        )
                    }
                }
            }
        }
    }
}
