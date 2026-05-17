package montafra.beam.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    var themeMode by remember { mutableStateOf(prefs.getString("themeMode", "system") ?: "system") }
    var customColorValue by remember { mutableIntStateOf(prefs.getInt("themeColorValue", colorSwatches[6])) }
    var heroBacklight by remember { mutableStateOf(prefs.getBoolean("heroBacklight", true)) }
    var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean("hapticsEnabled", true)) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keepScreenOn", false)) }
    var monoFont by remember { mutableStateOf(prefs.getBoolean("monoFont", false)) }

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
                                    val color = if (customColorValue != -1) customColorValue else colorSwatches[6]
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.heroBacklight)) },
                        supportingContent = { Text(stringResource(R.string.heroBacklightDesc)) },
                        trailingContent = {
                            Switch(
                                checked = heroBacklight,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    heroBacklight = it
                                    prefs.edit().putBoolean("heroBacklight", it).commit()
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            heroBacklight = !heroBacklight
                            prefs.edit().putBoolean("heroBacklight", heroBacklight).commit()
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.hapticsEnabled)) },
                        supportingContent = { Text(stringResource(R.string.hapticsEnabledDesc)) },
                        trailingContent = {
                            Switch(
                                checked = hapticsEnabled,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    hapticsEnabled = it
                                    prefs.edit().putBoolean("hapticsEnabled", it).commit()
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            hapticsEnabled = !hapticsEnabled
                            prefs.edit().putBoolean("hapticsEnabled", hapticsEnabled).commit()
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.keepScreenOn)) },
                        supportingContent = { Text(stringResource(R.string.keepScreenOnDesc)) },
                        trailingContent = {
                            Switch(
                                checked = keepScreenOn,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    keepScreenOn = it
                                    prefs.edit().putBoolean("keepScreenOn", it).commit()
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            keepScreenOn = !keepScreenOn
                            prefs.edit().putBoolean("keepScreenOn", keepScreenOn).commit()
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.monoFont)) },
                        supportingContent = { Text(stringResource(R.string.monoFontDesc)) },
                        trailingContent = {
                            Switch(
                                checked = monoFont,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    monoFont = it
                                    prefs.edit().putBoolean("monoFont", it).commit()
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            monoFont = !monoFont
                            prefs.edit().putBoolean("monoFont", monoFont).commit()
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

}
