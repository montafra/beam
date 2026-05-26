package montafra.beam.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import montafra.beam.BatteryData
import montafra.beam.BatteryViewModel
import montafra.beam.LocalHapticsEnabled
import montafra.beam.R
import montafra.beam.settingsName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, vm: BatteryViewModel = viewModel()) {
    val data by vm.data.collectAsState()
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.requestUpdate() }

    val heroBacklight = remember { mutableStateOf(
        context.getSharedPreferences(settingsName, Context.MODE_PRIVATE).getBoolean("heroBacklight", true)
    ) }
    val keepScreenOn = remember { mutableStateOf(
        context.getSharedPreferences(settingsName, Context.MODE_PRIVATE).getBoolean("keepScreenOn", false)
    ) }
    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences(settingsName, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "heroBacklight" -> heroBacklight.value = p.getBoolean(key, true)
                "keepScreenOn" -> keepScreenOn.value = p.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val view = LocalView.current
    DisposableEffect(keepScreenOn.value) {
        view.keepScreenOn = keepScreenOn.value
        onDispose { view.keepScreenOn = false }
    }

    val currentVersionCode = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt() }
        catch (_: Exception) { 0 }
    }
    var showChangelog by remember { mutableStateOf(false) }
    var changelogEntries by remember { mutableStateOf(emptyList<ChangelogEntry>()) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(settingsName, Context.MODE_PRIVATE)
        val lastSeen = prefs.getInt("lastSeenVersionCode", 0)
        if (currentVersionCode > lastSeen) {
            val entries = loadChangelogs(context, lastSeen, currentVersionCode)
            if (entries.isNotEmpty()) {
                changelogEntries = entries
                showChangelog = true
            } else {
                prefs.edit().putInt("lastSeenVersionCode", currentVersionCode).apply()
            }
        }
    }

    val noiseBitmap = remember {
        val sz = 256
        val bmp = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        val rng = java.util.Random(42L)
        val px = IntArray(sz * sz) {
            android.graphics.Color.argb(rng.nextInt(55).coerceIn(0, 255), 255, 255, 255)
        }
        bmp.setPixels(px, 0, sz, 0, 0, sz, sz)
        bmp
    }

    val grainBrush = remember(noiseBitmap) {
        ShaderBrush(
            android.graphics.BitmapShader(
                noiseBitmap,
                android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT,
            )
        )
    }

    // Slow candle-like glow: coprime durations, never sync → organic non-repeating flicker
    val candleTransition = rememberInfiniteTransition(label = "candle")
    val f1 by candleTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "f1",
    )
    val f2 by candleTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "f2",
    )
    val f3 by candleTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "f3",
    )
    val sway by candleTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val breathe by candleTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )

    val isOled = background == Color.Black
    val glowScale = when {
        background.luminance() > 0.5f -> 0.40f
        isOled -> 0.35f
        else -> 1.0f
    }

    Box(Modifier.fillMaxSize().background(background)) {
        if (!isOled) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(brush = grainBrush, alpha = 22f / 255f)
            }
        }

        Scaffold(
            modifier = Modifier,
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                    actions = {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate("settings")
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ico_settings),
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(40.dp))
                    ) {
                        if (heroBacklight.value) Canvas(Modifier.matchParentSize()) {
                            val w = size.width
                            val h = size.height
                            val cx = w * 0.50f + w * 0.04f * (sway * 2f - 1f)
                            val cy = h * 0.50f + h * 0.03f * breathe

                            val outerR = w * 0.76f * (0.90f + 0.10f * f3)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(primary.copy(alpha = (0.08f + 0.04f * f2) * glowScale), Color.Transparent),
                                    center = Offset(cx, cy), radius = outerR,
                                ),
                                radius = outerR, center = Offset(cx, cy),
                            )

                            val nOrbs = 6
                            val ringR    = w * 0.08f
                            val baseOrbR = w * 0.20f
                            for (i in 0 until nOrbs) {
                                val baseAngle = (i.toFloat() / nOrbs) * 2f * PI.toFloat() + sway * 0.5f
                                val driftX = ((f1 - 0.5f) * cos(i * 1.3f).toFloat() +
                                              (f2 - 0.5f) * sin(i * 2.1f).toFloat()) * w * 0.025f
                                val driftY = ((f2 - 0.5f) * cos(i * 1.7f).toFloat() +
                                              (f3 - 0.5f) * sin(i * 0.9f).toFloat()) * h * 0.018f
                                val ox = cx + ringR * cos(baseAngle).toFloat() + driftX
                                val oy = cy + ringR * sin(baseAngle).toFloat() + driftY
                                val sizeFactor = when (i % 5) {
                                    0    -> f1
                                    1    -> f2
                                    2    -> f3
                                    3    -> (f1 + f3) * 0.5f
                                    else -> (f2 + f1) * 0.5f
                                }
                                val orbRi = baseOrbR * (0.65f + 0.45f * sizeFactor)
                                val phase = when (i % 3) {
                                    0    -> f1 * 0.55f + f2 * 0.45f
                                    1    -> f2 * 0.55f + f3 * 0.45f
                                    else -> f3 * 0.55f + f1 * 0.45f
                                }
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(primary.copy(alpha = (0.14f + 0.17f * phase) * glowScale), Color.Transparent),
                                        center = Offset(ox, oy), radius = orbRi,
                                    ),
                                    radius = orbRi, center = Offset(ox, oy),
                                )
                            }

                            val coreR = w * 0.12f * (0.72f + 0.32f * f1)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(primary.copy(alpha = (0.26f + 0.18f * (f1 * f2)) * glowScale), Color.Transparent),
                                    center = Offset(cx, cy), radius = coreR,
                                ),
                                radius = coreR, center = Offset(cx, cy),
                            )
                        }
                        HeroCard(data)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    MetricCard(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
                    ) {
                        MetricRow(stringResource(R.string.power), data.power)
                        MetricRow(stringResource(R.string.current), data.current)
                        MetricRow(stringResource(R.string.voltage), data.voltage)
                        MetricRow(stringResource(R.string.temperature), data.temperature)
                        MetricRow(stringResource(R.string.energy), data.energy)
                    }
                    Spacer(Modifier.height(6.dp))
                    MetricCard(
                        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                    ) {
                        val rows = listOf(
                            stringResource(R.string.chargeLevel) to data.chargeLevel,
                            stringResource(R.string.charging) to data.charging,
                            stringResource(R.string.chargingSince) to data.chargingSince,
                            stringResource(R.string.timeToFullCharge) to data.timeToFullCharge,
                        ).filter { (_, v) -> v != "-" }
                        rows.forEach { (label, value) -> MetricRow(label, value) }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showChangelog) {
        ChangelogSheet(
            entries = changelogEntries,
            onDismiss = {
                showChangelog = false
                context.getSharedPreferences(settingsName, Context.MODE_PRIVATE)
                    .edit().putInt("lastSeenVersionCode", currentVersionCode).apply()
            },
        )
    }
}

@Composable
private fun HeroCard(data: BatteryData) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val scaleAnim = remember { Animatable(1f) }
    var jitterX by remember { mutableFloatStateOf(0f) }
    var jitterY by remember { mutableFloatStateOf(0f) }
    var holdActive by remember { mutableStateOf(false) }
    val lastTapMs = remember { LongArray(1) }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    val thudCapable = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
    }
    val thudEffect = remember(thudCapable) {
        if (thudCapable) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD)
                .compose()
        } else null
    }
    val playThud = {
        if (hapticsEnabled) {
            if (thudEffect != null) {
                vibrator.vibrate(thudEffect)
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    LaunchedEffect(holdActive) {
        if (!holdActive) {
            jitterX = 0f
            jitterY = 0f
            scaleAnim.animateTo(1f, spring(stiffness = 280f, dampingRatio = 0.4f))
            return@LaunchedEffect
        }
        val pxPerDp = with(density) { 1.dp.toPx() }
        coroutineScope {
            launch {
                scaleAnim.animateTo(0.86f, spring(stiffness = 420f, dampingRatio = 0.7f))
            }
            launch {
                val start = System.nanoTime()
                while (true) {
                    withFrameNanos { now ->
                        val t = (now - start) / 1e9f
                        jitterX = sin(t * 32f * 2f * PI.toFloat()) * 1.6f * pxPerDp
                        jitterY = cos(t * 38f * 2f * PI.toFloat()) * 1.6f * pxPerDp
                    }
                }
            }
            try {
                if (!hapticsEnabled) {
                    awaitCancellation()
                } else if (thudEffect != null) {
                    while (true) {
                        vibrator.vibrate(thudEffect)
                        delay(120)
                    }
                } else {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 60, 80),
                            intArrayOf(0, 220, 0),
                            0,
                        )
                    )
                    awaitCancellation()
                }
            } finally {
                vibrator.cancel()
            }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = data.chargeLevelFloat,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "charge-progress",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(40.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                        detectTapGestures(
                            onPress = { _ ->
                                val now = System.currentTimeMillis()
                                val isDouble = (now - lastTapMs[0]) < 300L
                                lastTapMs[0] = now
                                if (isDouble) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else {
                                    playThud()
                                    scope.launch {
                                        scaleAnim.animateTo(
                                            0.82f,
                                            spring(stiffness = 600f, dampingRatio = 0.65f),
                                        )
                                        scaleAnim.animateTo(
                                            1f,
                                            spring(stiffness = 280f, dampingRatio = 0.4f),
                                        )
                                    }
                                }
                                val longPressJob = scope.launch {
                                    delay(longPressTimeout)
                                    holdActive = true
                                }
                                tryAwaitRelease()
                                longPressJob.cancel()
                                holdActive = false
                            }
                        )
                    }
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
            Row(
                modifier = Modifier
                    .graphicsLayer {
                        val s = scaleAnim.value
                        scaleX = s
                        scaleY = s
                        translationX = jitterX
                        translationY = jitterY
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = data.power.removeSuffix("W"),
                    transitionSpec = {
                        (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.94f))
                            .togetherWith(fadeOut(tween(200)))
                    },
                    label = "power-value",
                ) { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.displayLarge,
                        color = onSurface,
                    )
                }
                Text(
                    text = "W",
                    style = MaterialTheme.typography.displayLarge,
                    color = onSurface,
                )
            }
            }
            Spacer(Modifier.height(4.dp))
            Canvas(Modifier.fillMaxWidth(0.55f).height(5.dp)) {
                val half = size.height / 2f
                val y = half
                drawLine(
                    color = primary.copy(alpha = 0.18f),
                    start = Offset(half, y),
                    end = Offset(size.width - half, y),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                if (animatedProgress > 0f) {
                    drawLine(
                        color = primary,
                        start = Offset(half, y),
                        end = Offset(half + (size.width - size.height) * animatedProgress, y),
                        strokeWidth = size.height,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = data.chargeLevel,
                style = MaterialTheme.typography.labelMedium,
                color = onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun MetricCard(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "metric-$label",
        ) { v ->
            Text(
                text = v,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
