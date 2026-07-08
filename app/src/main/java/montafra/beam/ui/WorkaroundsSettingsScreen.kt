package montafra.beam.ui

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import montafra.beam.BatteryViewModel
import montafra.beam.R
import montafra.beam.VendorBatteryHints
import montafra.beam.settingsName
import montafra.beam.settingsUpdateInd
import montafra.beam.ui.theme.BeamCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkaroundsSettingsScreen(navController: BeamNavController, vm: BatteryViewModel = viewModel()) {
    val data by vm.data.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    // Refreshed on resume so the warning clears when the user returns from the
    // system settings page they just fixed.
    var backgroundRestricted by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val am = context.getSystemService(ActivityManager::class.java)
                backgroundRestricted = am?.isBackgroundRestricted == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pollIntervals = listOf(1_250L, 1_750L, 2_500L, 3_500L, 5_000L, 7_500L, 10_000L)
    val pollLabels = listOf("1.25s", "1.75s", "2.5s", "3.5s", "5s", "7.5s", "10s")

    var currentScalar by remember { mutableFloatStateOf(prefs.getFloat("currentScalar", 1f)) }
    var invertCurrent by remember { mutableStateOf(prefs.getBoolean("invertCurrent", false)) }
    var useFahrenheit by remember { mutableStateOf(prefs.getBoolean("useFahrenheit", false)) }
    var pollIndex by remember {
        mutableIntStateOf(pollIntervals.indexOf(prefs.getLong("pollIntervalMs", 1_750L)).coerceAtLeast(0))
    }

    fun saveWorkarounds() {
        prefs.edit()
            .putFloat("currentScalar", currentScalar)
            .putBoolean("invertCurrent", invertCurrent)
            .putBoolean("useFahrenheit", useFahrenheit)
            .putLong("pollIntervalMs", pollIntervals[pollIndex])
            .commit()
        context.sendBroadcast(
            Intent().setPackage(context.packageName).setAction(settingsUpdateInd)
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.workarounds),
        onBack = { navController.popBackStack() },
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
                val scalarOptions = remember { listOf("0.001×", "0.5×", "1×", "2×", "1000×") }
                val scalarValues = remember { listOf(0.001f, 0.5f, 1f, 2f, 1000f) }
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
                            label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
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
                        ToggleSettingRow(
                            title = stringResource(R.string.invertCurrent),
                            description = stringResource(R.string.invertCurrentDesc),
                            checked = invertCurrent,
                            onCheckedChange = {
                                invertCurrent = it
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
            item {
                val hasVendor = VendorBatteryHints.current != null
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                ) {
                    ToggleSettingRow(
                        title = stringResource(R.string.useFahrenheit),
                        description = stringResource(R.string.useFahrenheitDesc),
                        checked = useFahrenheit,
                        onCheckedChange = {
                            useFahrenheit = it
                            saveWorkarounds()
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = if (hasVendor)
                        RoundedCornerShape(4.dp)
                    else
                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.batteryUsage)) },
                        supportingContent = { Text(stringResource(R.string.batteryUsageDesc)) },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ico_open_in_new),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            try {
                                // Battery usage screen (per-app breakdown); AOSP/Pixel only,
                                // there is no public intent action for it
                                context.startActivity(
                                    Intent().setComponent(
                                        ComponentName(
                                            "com.android.settings",
                                            "com.android.settings.Settings\$PowerUsageAdvancedActivity",
                                        )
                                    )
                                )
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
                                } catch (_: Exception) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null),
                                        )
                                    )
                                }
                            }
                        },
                    )
                }
                if (hasVendor) {
                    Spacer(Modifier.height(4.dp))
                    BeamCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                    ) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.vendorBatteryTitle)) },
                            supportingContent = {
                                if (backgroundRestricted) {
                                    Text(
                                        text = stringResource(R.string.vendorBatteryRestricted),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                } else {
                                    Text(stringResource(R.string.vendorBatteryDesc))
                                }
                            },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ico_open_in_new),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                VendorBatteryHints.openVendorSettings(context)
                            },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.learnMore)) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ico_open_in_new),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(VendorBatteryHints.dontKillUrl()))
                                    )
                                } catch (_: Exception) {
                                }
                            },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
    }
}
