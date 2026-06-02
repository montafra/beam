package montafra.beam.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import montafra.beam.R
import montafra.beam.applyNightMode
import montafra.beam.settingsName
import montafra.beam.ui.theme.BeamCard
import montafra.beam.ui.theme.fontFamilyFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    var themeMode by remember { mutableStateOf(prefs.getString("themeMode", "system") ?: "system") }
    var customColorValue by remember { mutableIntStateOf(prefs.getInt("themeColorValue", colorSwatches[5])) }
    var heroBacklight by remember { mutableStateOf(prefs.getBoolean("heroBacklight", true)) }
    var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean("hapticsEnabled", true)) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keepScreenOn", false)) }
    var fontFamily by remember { mutableStateOf(prefs.getString("fontFamily", "default") ?: "default") }
    var outlineOnlyCards by remember { mutableStateOf(prefs.getBoolean("outlineOnlyCards", false)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theme)) },
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
                SubLabel(stringResource(R.string.themeMode))
                Spacer(Modifier.height(8.dp))
                val modeOptions = listOf(
                    stringResource(R.string.themeModeSystem),
                    stringResource(R.string.themeModeLight),
                    stringResource(R.string.themeModeDark),
                    stringResource(R.string.themeModeOled),
                )
                val modeKeys = listOf("system", "light", "dark", "oled")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modeOptions.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = themeMode == modeKeys[i],
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                themeMode = modeKeys[i]
                                prefs.edit().putString("themeMode", themeMode).commit()
                                applyNightMode(themeMode)
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, modeOptions.size),
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                SubLabel(stringResource(R.string.themeColor))
                Spacer(Modifier.height(8.dp))
                val colorOptions = listOf(
                    stringResource(R.string.themeColorAuto),
                    stringResource(R.string.themeColorCustom),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    colorOptions.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = if (i == 0) customColorValue == -1 else customColorValue != -1,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (i == 0) {
                                    customColorValue = -1
                                    prefs.edit().putInt("themeColorValue", -1).commit()
                                } else {
                                    val color = if (customColorValue != -1) customColorValue else colorSwatches[5]
                                    customColorValue = color
                                    prefs.edit().putInt("themeColorValue", color).commit()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, colorOptions.size),
                            label = { Text(label) },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = customColorValue != -1,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        ColorSwatchPicker(
                            selectedColor = customColorValue.takeIf { it != -1 },
                            onColorSelected = { color ->
                                customColorValue = color
                                prefs.edit().putInt("themeColorValue", color).commit()
                            },
                        )
                    }
                }
            }
            item {
                SubLabel(stringResource(R.string.customization))
                Spacer(Modifier.height(8.dp))
                val fontKeys = listOf("default", "inter", "dm_sans", "space_grotesk", "jetbrains_mono", "noto_sans_mono")
                val fontLabels = listOf(
                    stringResource(R.string.fontDefault), "Inter", "DM Sans", "Space Grotesk", "JetBrains Mono", "Noto Sans Mono",
                )
                var fontMenuExpanded by remember { mutableStateOf(false) }
                val selectedFontLabel = fontLabels[fontKeys.indexOf(fontFamily).takeIf { it >= 0 } ?: 0]
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                fontMenuExpanded = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.font), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.fontDesc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(modifier = Modifier.padding(start = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    selectedFontLabel,
                                    fontFamily = fontFamilyFor(fontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ico_arrow_drop_down),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = fontMenuExpanded,
                                onDismissRequest = { fontMenuExpanded = false },
                            ) {
                                fontKeys.forEachIndexed { i, key ->
                                    DropdownMenuItem(
                                        text = { Text(fontLabels[i], fontFamily = fontFamilyFor(key)) },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            fontFamily = key
                                            prefs.edit().putString("fontFamily", key).commit()
                                            montafra.beam.BeamTempWidgetProvider.requestUpdate(context)
                                            fontMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                BeamCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
                    ThemeToggleRow(
                        title = stringResource(R.string.outlinedCards),
                        description = stringResource(R.string.outlinedCardsDesc),
                        checked = outlineOnlyCards,
                        onToggle = {
                            outlineOnlyCards = it
                            prefs.edit().putBoolean("outlineOnlyCards", it).commit()
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                BeamCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
                    ThemeToggleRow(
                        title = stringResource(R.string.heroBacklight),
                        description = stringResource(R.string.heroBacklightDesc),
                        checked = heroBacklight,
                        onToggle = {
                            heroBacklight = it
                            prefs.edit().putBoolean("heroBacklight", it).commit()
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                BeamCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
                    ThemeToggleRow(
                        title = stringResource(R.string.hapticsEnabled),
                        description = stringResource(R.string.hapticsEnabledDesc),
                        checked = hapticsEnabled,
                        onToggle = {
                            hapticsEnabled = it
                            prefs.edit().putBoolean("hapticsEnabled", it).commit()
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                ) {
                    ThemeToggleRow(
                        title = stringResource(R.string.keepScreenOn),
                        description = stringResource(R.string.keepScreenOnDesc),
                        checked = keepScreenOn,
                        onToggle = {
                            keepScreenOn = it
                            prefs.edit().putBoolean("keepScreenOn", it).commit()
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

}

@Composable
private fun ThemeToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle(it)
            },
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
