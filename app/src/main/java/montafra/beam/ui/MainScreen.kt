package montafra.beam.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import android.graphics.RenderEffect
import android.graphics.Shader
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
import androidx.compose.ui.graphics.asComposeRenderEffect
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
import montafra.beam.ui.theme.BeamCard

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

    // Single morphing glow: 3 slow, coprime, linear-eased phases drift overlapping metaball
    // lobes so they read as one amorphous shape (no separately trackable orbs), plus a slow breath.
    val glow = rememberInfiniteTransition(label = "glow")
    val p1 by glow.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_300, easing = LinearEasing), RepeatMode.Reverse),
        label = "p1",
    )
    val p2 by glow.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_700, easing = LinearEasing), RepeatMode.Reverse),
        label = "p2",
    )
    val p3 by glow.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_500, easing = LinearEasing), RepeatMode.Reverse),
        label = "p3",
    )
    val breathe by glow.animateFloat(
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
                        val glowMod = Modifier.matchParentSize().let { m ->
                            if (Build.VERSION.SDK_INT >= 31) m.graphicsLayer {
                                renderEffect = RenderEffect
                                    .createBlurEffect(40f, 40f, Shader.TileMode.DECAL)
                                    .asComposeRenderEffect()
                            } else m
                        }
                        if (heroBacklight.value) Canvas(glowMod) {
                            val w = size.width
                            val h = size.height
                            val a = (p1 - 0.5f) * 2f   // -1..1 waves
                            val b = (p2 - 0.5f) * 2f
                            val c = (p3 - 0.5f) * 2f
                            val br = breathe

                            val cx = w * 0.50f + w * 0.05f * a   // gentle whole-shape sway, centered
                            val cy = h * 0.50f + h * 0.03f * b   // centered, no directional bias

                            // Ambient halo — steady, large, fills gaps so the union never splits.
                            run {
                                val r = w * 0.78f * (0.92f + 0.08f * br)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(primary.copy(alpha = (0.07f + 0.03f * br) * glowScale), Color.Transparent),
                                        center = Offset(cx, cy), radius = r,
                                    ),
                                    radius = r, center = Offset(cx, cy),
                                )
                            }

                            // 3 metaball lobes on phase-offset Lissajous paths (each steered by a
                            // different pair of phases). Large radius vs small drift ⇒ always overlapping
                            // = one shape. Symmetric wander, no vertical bias.
                            // entry = (driftA, driftB, baseAngle, rFrac, rPulse, alpha)
                            val lobes = listOf(
                                listOf(a, b, -0.35f, 0.40f, c, 0.13f),
                                listOf(b, c,  0.55f, 0.36f, a, 0.12f),
                                listOf(c, a,  0.05f, 0.42f, b, 0.14f),
                            )
                            for (l in lobes) {
                                val dA = l[0]; val dB = l[1]; val ang = l[2]
                                val rFrac = l[3]; val rPulse = l[4]; val alpha = l[5]
                                val ox = cx + w * 0.085f * dA + w * 0.045f * cos(ang + dB * 0.8f).toFloat()
                                val oy = cy + h * 0.06f * dB + h * 0.045f * sin(ang + dA * 0.8f).toFloat()
                                val r = w * rFrac * (0.85f + 0.15f * (rPulse * 0.5f + 0.5f))
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(primary.copy(alpha = alpha * (0.80f + 0.20f * br) * glowScale), Color.Transparent),
                                        center = Offset(ox, oy), radius = r,
                                    ),
                                    radius = r, center = Offset(ox, oy),
                                )
                            }

                            // Steady bright core — keeps a hot center so the shape never reads as hollow/split.
                            val coreR = w * 0.16f * (0.85f + 0.15f * br)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(primary.copy(alpha = (0.24f + 0.10f * br) * glowScale), Color.Transparent),
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
    val slowRiseCapable = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE)
    }
    val spinCapable = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                VibrationEffect.Composition.PRIMITIVE_SPIN,
            )
    }
    // Tap: a spin. Hold: tension that builds the longer you press. Release: a quick fall into a wobble.
    val spinEffect = remember(spinCapable) {
        if (spinCapable) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.6f)
                .compose()
        } else null
    }
    val resistEffect = remember(slowRiseCapable) {
        if (slowRiseCapable) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.55f)
                .compose()
        } else null
    }
    val wobbleEffect = remember(spinCapable) {
        if (spinCapable) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.7f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.5f)
                .compose()
        } else null
    }
    // Fallbacks for devices without haptic primitives.
    val spinWaveform = remember {
        VibrationEffect.createWaveform(
            longArrayOf(0, 30, 25, 35, 25, 40),
            intArrayOf(0, 120, 50, 160, 50, 90),
            -1,
        )
    }
    val resistWaveform = remember {
        VibrationEffect.createWaveform(
            longArrayOf(0, 90, 90, 90, 130),
            intArrayOf(0, 45, 90, 140, 105),
            3,
        )
    }
    val wobbleWaveform = remember {
        VibrationEffect.createWaveform(
            longArrayOf(0, 45, 40, 45, 40, 45, 35),
            intArrayOf(0, 200, 45, 140, 30, 80, 0),
            -1,
        )
    }
    val playSpin = {
        if (hapticsEnabled) {
            vibrator.vibrate(spinEffect ?: spinWaveform)
        }
    }

    LaunchedEffect(holdActive) {
        val pxPerDp = with(density) { 1.dp.toPx() }
        if (holdActive) {
            // Hold: a firm, non-bouncy squeeze with resistance that builds.
            launch {
                scaleAnim.animateTo(0.84f, spring(stiffness = 200f, dampingRatio = 1f))
            }
            // Continuous turbulent vibration of the text for the whole hold.
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
            if (hapticsEnabled) {
                try {
                    if (resistEffect != null) {
                        while (true) {
                            vibrator.vibrate(resistEffect)
                            delay(360)
                        }
                    } else {
                        vibrator.vibrate(resistWaveform)
                        awaitCancellation()
                    }
                } finally {
                    vibrator.cancel()
                }
            }
        } else {
            vibrator.cancel()
            // Release: wobble back — only if actually squeezed (skips the initial composition).
            if (scaleAnim.value < 0.995f) {
                if (hapticsEnabled) {
                    vibrator.vibrate(wobbleEffect ?: wobbleWaveform)
                }
                launch {
                    val start = System.nanoTime()
                    val durationMs = 520f
                    while (true) {
                        var finished = false
                        withFrameNanos { now ->
                            val progress = (now - start) / 1e6f / durationMs
                            if (progress >= 1f) {
                                jitterX = 0f
                                jitterY = 0f
                                finished = true
                            } else {
                                val t = (now - start) / 1e9f
                                val decay = 1f - progress
                                val amp = 6f * pxPerDp * decay * decay
                                jitterX = sin(t * 24f * 2f * PI.toFloat()) * amp
                                jitterY = cos(t * 19f * 2f * PI.toFloat()) * amp
                            }
                        }
                        if (finished) break
                    }
                }
                scaleAnim.animateTo(1f, spring(stiffness = 240f, dampingRatio = 0.3f))
            } else {
                jitterX = 0f
                jitterY = 0f
            }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = data.chargeLevelFloat,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "charge-progress",
    )

    BeamCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
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
                                    playSpin()
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
    BeamCard(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
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
