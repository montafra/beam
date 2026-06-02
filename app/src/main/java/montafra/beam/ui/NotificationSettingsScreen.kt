package montafra.beam.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
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
import montafra.beam.settingsName
import montafra.beam.settingsUpdateInd
import montafra.beam.ui.theme.BeamCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    var indicatorEntries by remember {
        mutableStateOf(prefs.getStringSet("indicatorEntries", null) ?: emptySet())
    }
    var notificationIndicator by remember {
        mutableStateOf(prefs.getString("notificationIndicator", "W") ?: "W")
    }
    var showTimeToFull by remember { mutableStateOf(prefs.getBoolean("showTimeToFull", true)) }

    fun saveIndicatorEntries() {
        prefs.edit().putStringSet("indicatorEntries", indicatorEntries).commit()
        context.sendBroadcast(Intent().setPackage(context.packageName).setAction(settingsUpdateInd))
    }

    fun saveNotificationIndicator() {
        prefs.edit().putString("notificationIndicator", notificationIndicator).commit()
        context.sendBroadcast(Intent().setPackage(context.packageName).setAction(settingsUpdateInd))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notification)) },
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
                val metricLabels = remember { listOf("W", "A", "Ah", "°C", "V", "Wh", "%") }
                val metricKeys   = remember { listOf("W", "A", "Ah", "C",  "V", "Wh", "%") }
                SubLabel(stringResource(R.string.notificationIcon))
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    metricKeys.forEachIndexed { i, key ->
                        SegmentedButton(
                            selected = notificationIndicator == key,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                notificationIndicator = key
                                saveNotificationIndicator()
                                if (key in indicatorEntries) {
                                    indicatorEntries = indicatorEntries - key
                                    saveIndicatorEntries()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, metricKeys.size),
                            label = { Text(metricLabels[i]) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                SubLabel(stringResource(R.string.statusBarIndicator))
                Spacer(Modifier.height(8.dp))
                MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    metricKeys.forEachIndexed { i, key ->
                        SegmentedButton(
                            checked = key in indicatorEntries,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                indicatorEntries = if (key in indicatorEntries)
                                    indicatorEntries - key
                                else
                                    indicatorEntries + key
                                saveIndicatorEntries()
                            },
                            enabled = notificationIndicator != key,
                            shape = SegmentedButtonDefaults.itemShape(i, metricKeys.size),
                            label = { Text(metricLabels[i]) },
                        )
                    }
                }
            }
            item {
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.showTimeToFull)) },
                        supportingContent = { Text(stringResource(R.string.showTimeToFullDesc)) },
                        trailingContent = {
                            Switch(
                                checked = showTimeToFull,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showTimeToFull = it
                                    prefs.edit().putBoolean("showTimeToFull", it).commit()
                                    context.sendBroadcast(
                                        Intent().setPackage(context.packageName).setAction(settingsUpdateInd)
                                    )
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showTimeToFull = !showTimeToFull
                            prefs.edit().putBoolean("showTimeToFull", showTimeToFull).commit()
                            context.sendBroadcast(
                                Intent().setPackage(context.packageName).setAction(settingsUpdateInd)
                            )
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
