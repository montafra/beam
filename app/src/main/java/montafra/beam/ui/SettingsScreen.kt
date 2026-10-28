package montafra.beam.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import montafra.beam.LocalHapticsEnabled
import montafra.beam.R
import montafra.beam.StatusService
import montafra.beam.settingsName
import montafra.beam.settingsUpdateInd
import montafra.beam.ui.theme.BeamCard
import montafra.beam.ui.theme.beamWeight
import montafra.beam.ui.theme.CardGap
import montafra.beam.ui.theme.cardShapeBottom
import montafra.beam.ui.theme.cardShapeMiddle
import montafra.beam.ui.theme.cardShapeSingle
import montafra.beam.ui.theme.cardShapeTop
import montafra.beam.ui.theme.defaultSeedColor
import montafra.beam.ui.theme.hct.Hct

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(navController: BeamNavController) {
    val context = LocalContext.current
    val haptic = LocalTapHaptics.current
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    val hapticsEnabled = LocalHapticsEnabled.current
    val playTapSound = rememberTapSound()
    val notificationToggleHaptic = { enabled: Boolean ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // This branch bypasses LocalTapHaptics, so the tap sound has to be played by hand.
            playTapSound()
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
    // The sheet flags are saveable: the activity is recreated on rotation now that the portrait
    // lock is gone, and an open sheet vanishing under the user is not a rotation.
    var showDonateDialog by rememberSaveable { mutableStateOf(false) }
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
    var showChangelog by rememberSaveable { mutableStateOf(false) }
    // The entries themselves aren't saveable, so a restored sheet refills them below rather than
    // carrying the whole parsed changelog through the bundle.
    var changelogEntries by remember { mutableStateOf(emptyList<ChangelogEntry>()) }
    var showAppInfo by rememberSaveable { mutableStateOf(false) }
    val appInfoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(showChangelog) {
        if (showChangelog && changelogEntries.isEmpty()) {
            changelogEntries = loadChangelogs(context, 0, currentVersionCode)
        }
    }

    val applyNotificationEnabled = { enabled: Boolean ->
        notificationEnabled = enabled
        prefs.edit().putBoolean("notificationEnabled", enabled).commit()
        if (enabled) {
            context.startForegroundService(Intent(context, StatusService::class.java))
        } else {
            context.sendBroadcast(Intent(settingsUpdateInd).setPackage(context.packageName))
        }
        montafra.beam.BeamNotificationTileService.requestRefresh(context)
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

    SettingsScaffold(
        title = stringResource(R.string.settings),
        onBack = { navController.popBackStack() },
    ) {
            // Interface
            item {
                SectionHeader(stringResource(R.string.interfaceSection))
                Spacer(Modifier.height(8.dp))
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShapeTop(),
                ) {
                    ToggleSettingRow(
                        title = stringResource(R.string.notification),
                        description = stringResource(R.string.notificationEnableDesc),
                        checked = notificationEnabled,
                        onCheckedChange = setNotificationEnabled,
                        leadingIcon = painterResource(R.drawable.ico_notification),
                        onHaptic = notificationToggleHaptic,
                    )
                }
                CardGap()
                AnimatedVisibility(
                    visible = notificationEnabled,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(220)) + fadeOut(tween(180)),
                ) {
                    Column {
                        BeamCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = cardShapeMiddle(),
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
                        CardGap()
                    }
                }
                val showLanguage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = if (showLanguage) cardShapeMiddle() else cardShapeBottom(),
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
                    CardGap()
                    BeamCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShapeBottom(),
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
                    shape = cardShapeSingle(),
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
                    shape = cardShapeTop(),
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
                CardGap()
                BeamCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShapeBottom(),
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

    if (showDonateDialog) {
        BeamSheet(
            onDismiss = { showDonateDialog = false },
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
                        shape = cardShapeTop(),
                        iconRes = R.drawable.ico_liberapay,
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
                    CardGap()
                    CryptoDonateCard("Bitcoin", "bitcoin", "bc1q7v38g2xn7wxtwn6ewde4kydn5emjr3zt73ew96", clipboardManager, cardShapeMiddle(), R.drawable.ico_btc)
                    CardGap()
                    CryptoDonateCard("Monero", "monero", "876wwukGWhU9H6qez4Qmt5gTBBmdKzoDg3zvT33QCwjy9e7jS7MVjQySUCpNhoVrFcF15AicUJ4VaVrTKAXGMu5D7yUbqFs", clipboardManager, cardShapeMiddle(), R.drawable.ico_xmr)
                    CardGap()
                    CryptoDonateCard("Lightning", "lightning", "monta@cake.cash", clipboardManager, cardShapeBottom(), R.drawable.ico_lightning)
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
        BeamSheet(
            onDismiss = { showAppInfo = false },
            sheetState = appInfoSheetState,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BeamCard(
                    modifier = Modifier.size(88.dp),
                    shape = CircleShape,
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
                        style = MaterialTheme.typography.headlineSmall.beamWeight(FontWeight.Bold),
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
                        text = stringResource(R.string.byAuthor, "montafra"),
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
                            shape = cardShapeTop(radius = 16.dp),
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
                                    // Full size, unlike the 18dp open-in-new glyphs below: the
                                    // chevron only fills a third of its viewport, so any smaller
                                    // and it reads as a speck next to them.
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        CardGap()
                        BeamCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = cardShapeMiddle(),
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
                        CardGap()
                        BeamCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = cardShapeBottom(radius = 16.dp),
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
                        text = stringResource(R.string.privacyStatement),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.licenseCopyright),
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

/** Where [defaultSeedColor] sits in [colorSwatches], and the hue the other eleven turn around. */
private const val DefaultSwatchIndex = 6

/**
 * The twelve presets: a full turn of hue in 30 degree steps at the default seed's own chroma and
 * tone, so the grid reads as one ramp rather than twelve unrelated colours - warm across the top
 * row, cool across the bottom.
 *
 * The anchor is the literal [defaultSeedColor], not a generated colour, because [ColorSwatchPicker]
 * marks the selection by exact identity and a gamut round trip does not always land back on the
 * ARGB it started from - generating it would leave a fresh install with nothing selected.
 *
 * Hct.from clamps chroma to whatever sRGB actually holds at each hue and tone, so the cool half
 * comes out gentler than the warm half. That is the shape of the gamut, not a bug.
 *
 * Lazy, not eager: eleven gamut searches in this file's static initializer would be paid on the
 * main thread by every screen that touches anything top-level here, [SubLabel] included. Deferred,
 * only the Theme screen that actually draws the grid pays for it.
 */
private val colorSwatches: List<Int> by lazy {
    Hct.fromInt(defaultSeedColor).let { seed ->
        List(12) { i ->
            if (i == DefaultSwatchIndex) defaultSeedColor
            else Hct.from(seed.hue + (i - DefaultSwatchIndex) * 30.0, seed.chroma, seed.tone).argb
        }
    }
}

/** Whether [color] is one of the twelve presets, i.e. a colour the grid itself will mark. */
internal fun isPresetSwatch(color: Int): Boolean = color in colorSwatches

/**
 * Two rows of six, each row sharing the full width so the swatches grow with the screen rather
 * than floating in it. The rows have no spacing of their own: every swatch's hit box already
 * extends half a [SwatchGap] above and below the drawn shape, so stacking them flush leaves the
 * same gap between rows as [SwatchGap] leaves between columns.
 */
@Composable
internal fun ColorSwatchPicker(
    selectedColor: Int?,
    onColorSelected: (Int) -> Unit,
) {
    Column {
        colorSwatches.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SwatchGap)) {
                row.forEach { color ->
                    ColorSwatch(
                        color,
                        selected = selectedColor == color,
                        modifier = Modifier.weight(1f),
                    ) { onColorSelected(color) }
                }
            }
        }
    }
}

/**
 * A crypto donation row: a tap hands the address straight to a wallet app through its URI scheme,
 * a long press copies the raw address - the same split the Liberapay row above it uses.
 *
 * Not having a wallet for a given chain is the ordinary case here rather than the edge one, so a
 * missing handler isn't guarded against so much as answered: copy the address and say why nothing
 * opened. Catching the exception also keeps the manifest clean, since package visibility only
 * restricts resolveActivity(), never launching an implicit intent.
 */
@Composable
private fun CryptoDonateCard(
    label: String,
    scheme: String,
    address: String,
    clipboard: ClipboardManager,
    shape: androidx.compose.ui.graphics.Shape,
    iconRes: Int,
) {
    val context = LocalContext.current
    // Resolved out here: a click lambda is not a composable scope.
    val noWallet = stringResource(R.string.noWalletApp)
    DonateCard(
        label = label,
        address = address,
        clipboard = clipboard,
        shape = shape,
        iconRes = iconRes,
        actionIconRes = R.drawable.ico_open_in_new,
        actionContentDescRes = R.string.openInWallet,
        onActionClick = { cb ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$scheme:$address")))
            } catch (_: ActivityNotFoundException) {
                cb.setPrimaryClip(ClipData.newPlainText(label, address))
                Toast.makeText(context, noWallet, Toast.LENGTH_SHORT).show()
            }
        },
        // The bare address, not the URI - it's what a wallet elsewhere expects pasted in.
        onLongClick = { cb -> cb.setPrimaryClip(ClipData.newPlainText(label, address)) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DonateCard(
    label: String,
    address: String,
    clipboard: ClipboardManager,
    shape: androidx.compose.ui.graphics.Shape = cardShapeSingle(),
    iconRes: Int? = null,
    actionIconRes: Int = R.drawable.ico_copy,
    actionContentDescRes: Int = R.string.copy,
    onActionClick: (ClipboardManager) -> Unit = {
        it.setPrimaryClip(ClipData.newPlainText(label, address))
    },
    onLongClick: ((ClipboardManager) -> Unit)? = null,
) {
    val haptic = LocalTapHaptics.current
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
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The drawn height of a swatch, and the side of the custom colour button's dot. Half of it is the
 * pill radius an unselected one rests at.
 */
internal val SwatchHeight = 40.dp

/** The gap between swatches: between columns directly, between rows via [SwatchHitHeight]. */
private val SwatchGap = 8.dp

/**
 * The tap target height: the drawn shape plus half a gap above and below, so a tap in the gap
 * between rows still lands on the nearer swatch. The width is whatever the row hands out.
 */
private val SwatchHitHeight = SwatchHeight + SwatchGap

/** Corner radius of a selected swatch - squared off into a rectangle, no longer a pill. */
private val SwatchSelectedRadius = 8.dp

/**
 * The corner radius of a [SwatchHeight]-tall shape as it toggles between a resting pill (a circle,
 * on a square) and a selected rounded rectangle - the same shape language as Material's expressive
 * toggle buttons. Shared by the grid swatches and the custom colour button's dot so the two move
 * on the same spring.
 *
 * The spring mirrors MotionScheme.fastSpatial by hand, since the material3 on the classpath ships
 * no public MotionScheme yet (see BeamTheme). Its overshoot is clamped on the pill side - a radius
 * past half the height is no longer a pill - and left alone on the way to the rectangle, where a
 * dip below the selected radius just reads as the corners settling.
 */
@Composable
internal fun swatchCornerRadius(selected: Boolean): Dp {
    val radius by animateDpAsState(
        targetValue = if (selected) SwatchSelectedRadius else SwatchHeight / 2,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "swatchCorner",
    )
    return radius.coerceAtMost(SwatchHeight / 2)
}

/** Width of the ring a selected swatch wears - the same ring ColorStyleChip draws on its preview. */
private val SwatchRingWidth = 3.dp

/**
 * Shapes and fills a swatch: the [swatchCornerRadius] morph, plus an onSurface ring that grows in
 * as it squares off. Shared by the grid swatches and the custom colour button's dot, so every
 * selection on the Theme page - the colour style chips included - is marked the same way.
 *
 * The ring is ColorStyleChip's double clip rather than a border: an onSurface fill behind, inset,
 * then the colour clipped over it. A border can't animate down to nothing, since a 0dp one is
 * drawn as a hairline, and its width snaps to whole pixels on the way. The ring drawn inside the
 * shape's own bounds keeps the layout still while it grows.
 *
 * Critically damped where the corner spring bounces: an overshoot would push the width below zero
 * on the way out. Same stiffness, so the two still land together.
 */
@Composable
internal fun Modifier.swatchShape(selected: Boolean, color: Color): Modifier {
    val radius = swatchCornerRadius(selected)
    val ring by animateDpAsState(
        targetValue = if (selected) SwatchRingWidth else 0.dp,
        animationSpec = spring(dampingRatio = 1f, stiffness = 800f),
        label = "swatchRing",
    )
    return this
        .clip(RoundedCornerShape(radius))
        // Transparent at rest, not hidden under the colour: the clip's anti-aliased edge would
        // otherwise let a faint onSurface fringe through round every unselected swatch.
        .background(if (ring > 0.dp) MaterialTheme.colorScheme.onSurface else Color.Transparent)
        .padding(ring)
        .clip(RoundedCornerShape((radius - ring).coerceAtLeast(0.dp)))
        .background(color)
}

/**
 * A pill at rest that squares off into a rounded rectangle when selected, ringed, with a check
 * inside.
 *
 * The outer Box is the tap target and draws nothing; the ripple is drawn by the shaped Box inside
 * so it stays clipped to the swatch while the hit box stays taller than the shape.
 */
@Composable
internal fun ColorSwatch(
    color: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptic = LocalTapHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val swatch = Color(color)
    Box(
        modifier = modifier
            .height(SwatchHitHeight)
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SwatchHeight)
                .swatchShape(selected, swatch)
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ico_check),
                    contentDescription = null,
                    // The swatch is a saturated seed, so the glyph takes whichever of black or
                    // white survives on it - the same rule CustomColorButton applies to its dot.
                    tint = if (swatch.luminance() > 0.5f) Color.Black.copy(alpha = 0.7f) else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

