package montafra.beam.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import montafra.beam.LocalHapticsEnabled
import montafra.beam.R
import montafra.beam.StatusService
import montafra.beam.settingsName
import montafra.beam.settingsUpdateInd
import montafra.beam.ui.theme.BeamCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    val hapticsEnabled = LocalHapticsEnabled.current
    val notificationToggleHaptic = { enabled: Boolean ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hapticsEnabled) {
                view.performHapticFeedback(
                    if (enabled) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
                )
            }
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    var showDonateDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var notificationEnabled by remember { mutableStateOf(prefs.getBoolean("notificationEnabled", true)) }
    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }
        catch (_: Exception) { "" }
    }
    val currentVersionCode = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt() }
        catch (_: Exception) { 0 }
    }
    var showChangelog by remember { mutableStateOf(false) }
    var changelogEntries by remember { mutableStateOf(emptyList<ChangelogEntry>()) }
    var showAppInfo by remember { mutableStateOf(false) }
    val appInfoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val applyNotificationEnabled = { enabled: Boolean ->
        notificationEnabled = enabled
        prefs.edit().putBoolean("notificationEnabled", enabled).commit()
        if (enabled) {
            context.startForegroundService(Intent(context, StatusService::class.java))
        } else {
            context.sendBroadcast(Intent(settingsUpdateInd).setPackage(context.packageName))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) applyNotificationEnabled(true)
    }

    val setNotificationEnabled = { enabled: Boolean ->
        if (enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            applyNotificationEnabled(enabled)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Interface
            item {
                SectionHeader(stringResource(R.string.interfaceSection))
                Spacer(Modifier.height(8.dp))
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.notification)) },
                        supportingContent = { Text(stringResource(R.string.notificationEnableDesc)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ico_notification),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = notificationEnabled,
                                onCheckedChange = { enabled ->
                                    notificationToggleHaptic(enabled)
                                    setNotificationEnabled(enabled)
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            val next = !notificationEnabled
                            notificationToggleHaptic(next)
                            setNotificationEnabled(next)
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                AnimatedVisibility(
                    visible = notificationEnabled,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(220)) + fadeOut(tween(180)),
                ) {
                    Column {
                        BeamCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.advancedSettings)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.ico_notification_gear),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    navController.navigate("settings/notification")
                                },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                val showLanguage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(
                        topStart = 4.dp, topEnd = 4.dp,
                        bottomStart = if (showLanguage) 4.dp else 20.dp,
                        bottomEnd = if (showLanguage) 4.dp else 20.dp,
                    ),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.theme)) },
                        supportingContent = { Text(stringResource(R.string.themeDesc)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ico_theme),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("settings/theme")
                        },
                    )
                }
                if (showLanguage) {
                    Spacer(Modifier.height(4.dp))
                    BeamCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                    ) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.language)) },
                            supportingContent = { Text(stringResource(R.string.languageDesc)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ico_language),
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
                                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                    )
                                } catch (_: Exception) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // Workarounds
            item {
                SectionHeader(stringResource(R.string.advancedSection))
                Spacer(Modifier.height(8.dp))
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.workarounds)) },
                        supportingContent = { Text(stringResource(R.string.workaroundsShortDesc)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ico_settings),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("settings/workarounds")
                        },
                    )
                }
            }

            // About
            item {
                SectionHeader(stringResource(R.string.about))
                Spacer(Modifier.height(8.dp))
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.supportMe)) },
                        supportingContent = { Text("Liberapay · BTC · XMR · Lightning") },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ico_donate),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDonateDialog = true
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                ) {
                    ListItem(
                        headlineContent = { Text("Beam $version") },
                        supportingContent = { Text(stringResource(R.string.versionLicenseLinks)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ico_info),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showAppInfo = true
                        },
                    )
                }
            }
        }
    }

    if (showDonateDialog) {
        ModalBottomSheet(
            onDismissRequest = { showDonateDialog = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ico_donate),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(R.string.supportMe),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text(
                    text = stringResource(R.string.supportMeDesc),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    DonateCard(
                        label = "Liberapay",
                        address = "liberapay.com/montafra",
                        clipboard = clipboardManager,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        actionIconRes = R.drawable.ico_open_in_new,
                        actionContentDescRes = R.string.openInBrowser,
                        onActionClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://liberapay.com/montafra"))
                            )
                        },
                        onLongClick = {
                            it.setPrimaryClip(
                                ClipData.newPlainText("Liberapay", "https://liberapay.com/montafra")
                            )
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    DonateCard("Bitcoin", "bc1q7v38g2xn7wxtwn6ewde4kydn5emjr3zt73ew96", clipboardManager, RoundedCornerShape(4.dp))
                    Spacer(Modifier.height(4.dp))
                    DonateCard("Monero", "876wwukGWhU9H6qez4Qmt5gTBBmdKzoDg3zvT33QCwjy9e7jS7MVjQySUCpNhoVrFcF15AicUJ4VaVrTKAXGMu5D7yUbqFs", clipboardManager, RoundedCornerShape(4.dp))
                    Spacer(Modifier.height(4.dp))
                    DonateCard("Lightning", "monta@cake.cash", clipboardManager, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showChangelog) {
        ChangelogSheet(
            entries = changelogEntries,
            onDismiss = { showChangelog = false },
        )
    }

    if (showAppInfo) {
        ModalBottomSheet(
            onDismissRequest = { showAppInfo = false },
            sheetState = appInfoSheetState,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BeamCard(
                    modifier = Modifier.size(88.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ico_logo),
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(60.dp),
                        )
                    }
                }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "v$version",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    clipboardManager.setPrimaryClip(
                                        ClipData.newPlainText("Beam", "Beam $version")
                                    )
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "by montafra",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/montafra"))
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.appDescription),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BeamCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showAppInfo = false
                                        changelogEntries = loadChangelogs(context, 0, currentVersionCode)
                                        if (changelogEntries.isNotEmpty()) showChangelog = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ico_info),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    text = stringResource(R.string.changelog),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ico_chevron_right),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        BeamCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/montafra/beam"))
                                            )
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            clipboardManager.setPrimaryClip(
                                                ClipData.newPlainText("Source Code", "https://github.com/montafra/beam")
                                            )
                                        },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ico_github),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    text = stringResource(R.string.sourceCode),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ico_open_in_new),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        BeamCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/montafra/beam/issues"))
                                            )
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            clipboardManager.setPrimaryClip(
                                                ClipData.newPlainText("Issue Tracker", "https://github.com/montafra/beam/issues")
                                            )
                                        },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ico_bug),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    text = stringResource(R.string.issueTracker),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ico_open_in_new),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "No ads · No tracking · No data collection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "MIT · © 2026 Francesco Montalegni",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
internal fun SubLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal val colorSwatches = listOf(
    0xFFE53935, 0xFFF4511E, 0xFFFB8C00,
    0xFFFFB300, 0xFF7CB342, 0xFF43A047,
    0xFF00897B, 0xFF00ACC1, 0xFF1E88E5,
    0xFF3949AB, 0xFF8E24AA, 0xFFD81B60,
).map { it.toInt() }

@Composable
internal fun ColorSwatchPicker(
    selectedColor: Int?,
    onColorSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colorSwatches.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { color ->
                    ColorSwatch(color, selected = selectedColor == color) { onColorSelected(color) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DonateCard(
    label: String,
    address: String,
    clipboard: ClipboardManager,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    actionIconRes: Int = R.drawable.ico_copy,
    actionContentDescRes: Int = R.string.copy,
    onActionClick: (ClipboardManager) -> Unit = {
        it.setPrimaryClip(ClipData.newPlainText(label, address))
    },
    onLongClick: ((ClipboardManager) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val iconRes = when (label) {
        "Liberapay" -> R.drawable.ico_liberapay
        "Bitcoin" -> R.drawable.ico_btc
        "Monero" -> R.drawable.ico_xmr
        "Lightning" -> R.drawable.ico_lightning
        else -> null
    }
    val brandColor = when (label) {
        "Liberapay" -> Color(0xFFF6C915)
        "Bitcoin" -> Color(0xFFF7931A)
        "Monero" -> Color(0xFFFF6600)
        "Lightning" -> Color(0xFF9B59B6)
        else -> MaterialTheme.colorScheme.primary
    }
    BeamCard(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onActionClick(clipboard)
                    },
                    onLongClick = onLongClick?.let { handler ->
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            handler(clipboard)
                        }
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (iconRes != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(brandColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = brandColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onActionClick(clipboard)
                }
            ) {
                Icon(
                    painter = painterResource(actionIconRes),
                    contentDescription = stringResource(actionContentDescRes),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
            .padding(if (selected) 2.5.dp else 0.dp)
            .clip(CircleShape)
            .background(Color(color))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    )
}

