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
import android.graphics.RuntimeShader
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import montafra.beam.BatteryData
import montafra.beam.BatteryViewModel
import montafra.beam.LocalHapticsEnabled
import montafra.beam.R
import montafra.beam.settingsName
import montafra.beam.ui.theme.BeamCard
import montafra.beam.ui.theme.heroNumberFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, vm: BatteryViewModel = viewModel()) {
    val data by vm.data.collectAsState()
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hapticsEnabled = LocalHapticsEnabled.current

    // Easter egg: tap the "Beam" title 10 times in a row to make it spin.
    val titleSpin = remember { Animatable(0f) }
    var beamTaps by remember { mutableStateOf(0) }
    val lastBeamTapMs = remember { LongArray(1) }
    val spinVibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    // A subtle spin-flavored buzz that matches the title's spin.
    val spinHaptic = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            spinVibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_SPIN)) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.4f)
                .compose()
        } else null
    }

    LaunchedEffect(Unit) { vm.requestUpdate() }

    val heroBacklight = remember { mutableStateOf(
        context.getSharedPreferences(settingsName, Context.MODE_PRIVATE).getBoolean("heroBacklight", true)
    ) }
    val keepScreenOn = remember { mutableStateOf(
        context.getSharedPreferences(settingsName, Context.MODE_PRIVATE).getBoolean("keepScreenOn", false)
    ) }
    val showChargeLevel = remember { mutableStateOf(
        context.getSharedPreferences(settingsName, Context.MODE_PRIVATE).getBoolean("showChargeLevel", true)
    ) }
    val fontKey = remember { mutableStateOf(
        context.getSharedPreferences(settingsName, Context.MODE_PRIVATE).getString("fontFamily", "default") ?: "default"
    ) }
    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences(settingsName, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "heroBacklight" -> heroBacklight.value = p.getBoolean(key, true)
                "keepScreenOn" -> keepScreenOn.value = p.getBoolean(key, false)
                "showChargeLevel" -> showChargeLevel.value = p.getBoolean(key, true)
                "fontFamily" -> fontKey.value = p.getString(key, "default") ?: "default"
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
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            modifier = Modifier
                                .graphicsLayer { rotationZ = titleSpin.value }
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        val now = System.currentTimeMillis()
                                        if (now - lastBeamTapMs[0] > 800L) beamTaps = 0
                                        lastBeamTapMs[0] = now
                                        beamTaps++
                                        if (beamTaps >= 10) {
                                            beamTaps = 0
                                            if (hapticsEnabled) {
                                                if (spinHaptic != null) spinVibrator.vibrate(spinHaptic)
                                                else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            scope.launch {
                                                titleSpin.animateTo(
                                                    titleSpin.value + 720f,
                                                    spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.5f),
                                                )
                                            }
                                        }
                                    }
                                },
                        )
                    },
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
                        HeroCard(data, showChargeLevel.value, fontKey.value)
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

// AGSL domain-warp that melts the rendered number while it's held. The text is
// supplied as the `content` shader (the name MUST match the layer name passed to
// createRuntimeShaderEffect). Each sampled coordinate is pushed around by a couple
// of cheap crossed-axis sine layers whose phase advances with `time`, and a
// downward `droop` that grows toward the baseline so it reads as melting rather
// than shaking. Amplitude scales with `progress` (0 = identity), and everything is
// normalized against `size` so the look is resolution-independent.
private const val LIQUIFY_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float time;
    uniform float progress;

    half4 main(float2 coord) {
        float2 uv = coord / size;
        float amp = progress * 9.0;                       // max px displacement; keeps glyphs legible
        float wx = sin(uv.y * 11.0 + time * 1.7) + 0.5 * sin(uv.y * 23.0 - time * 2.3);
        float wy = sin(uv.x * 9.0  + time * 1.3) + 0.5 * sin(uv.x * 19.0 + time * 2.9);
        float droop = uv.y * progress * 6.0;              // melt bias: more droop toward baseline
        float2 offset = float2(wx * amp, wy * amp + droop);
        return content.eval(coord + offset);
    }
"""

@Composable
private fun HeroCard(data: BatteryData, showChargeLevel: Boolean, fontKey: String) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val scaleAnim = remember { Animatable(1f) }
    val tapWeight = remember { Animatable(700f) }
    var holdActive by remember { mutableStateOf(false) }
    val lastTapMs = remember { LongArray(1) }

    // Liquify hold (API 33+ only): a RuntimeShader melts the rendered number while
    // held, then un-melts on release. `warpProgress` is the 0..1 amount of melt;
    // `warpTime` advances every frame to animate the flow. Both default to a no-op
    // (0f) so the resting state and pre-33 path stay clean.
    val liquifyShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(LIQUIFY_AGSL) else null
    }
    val warpProgress = remember { Animatable(0f) }
    var warpTime by remember { mutableFloatStateOf(0f) }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    // Primitive support for the liquify haptics. THUD requires API 30 (R); these are
    // only ever played on the API 33+ path, so the query is always safe there.
    val splashCapable = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            )
    }
    // Hold start: a strong THUD then a settling run of fading ticks with growing gaps
    // — "starts strong, fades out", like a drop hitting liquid and rippling away.
    val splashEffect = remember(splashCapable) {
        if (splashCapable) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 0)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f, 90)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.45f, 150)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.25f, 230)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.12f, 320)
                .compose()
        } else null
    }
    // Fallback splash for devices without primitives: a single decaying waveform.
    val splashWaveform = remember {
        VibrationEffect.createWaveform(
            longArrayOf(0, 60, 70, 80, 100, 120),
            intArrayOf(0, 200, 130, 80, 45, 20),
            -1,
        )
    }
    // Release: a soft, short tick acknowledging the un-melt.
    val releaseTickEffect = remember(splashCapable) {
        if (splashCapable) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.5f, 0)
                .compose()
        } else null
    }
    val releaseTickWaveform = remember {
        VibrationEffect.createWaveform(longArrayOf(0, 22), intArrayOf(0, 110), -1)
    }

    LaunchedEffect(holdActive) {
        // The liquify hold (squeeze + warp + haptics) is API 33+ only. On older
        // devices a hold does nothing visually — only the tap weight-morph applies.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect

        if (holdActive) {
            // Firm, non-bouncy squeeze.
            launch { scaleAnim.animateTo(0.84f, spring(stiffness = 200f, dampingRatio = 1f)) }
            // Melt in.
            launch { warpProgress.animateTo(1f, spring(stiffness = 200f, dampingRatio = 1f)) }
            // Advance shader time every frame for as long as we're held. This loop is
            // cancelled when holdActive flips (the LaunchedEffect restarts); the release
            // branch starts its own loop to finish the un-melt.
            launch {
                val start = System.nanoTime()
                while (true) {
                    withFrameNanos { now -> warpTime = (now - start) / 1e9f }
                }
            }
            // One-shot decaying splash on hold start.
            if (hapticsEnabled) {
                vibrator.vibrate(splashEffect ?: splashWaveform)
            }
        } else {
            vibrator.cancel()
            // Release: reverse the melt — only if we actually melted (skips quick taps).
            if (warpProgress.value > 0.001f) {
                if (hapticsEnabled) {
                    vibrator.vibrate(releaseTickEffect ?: releaseTickWaveform)
                }
                // Keep advancing time so the un-melt still flows, until progress settles.
                launch {
                    val start = System.nanoTime()
                    while (warpProgress.value > 0.001f || warpProgress.isRunning) {
                        withFrameNanos { now -> warpTime = (now - start) / 1e9f }
                    }
                }
                launch { scaleAnim.animateTo(1f, spring(stiffness = 240f, dampingRatio = 0.3f)) }
                warpProgress.animateTo(0f, spring(stiffness = 240f, dampingRatio = 1f))
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
                .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = if (showChargeLevel) 28.dp else 8.dp),
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
                                    if (hapticsEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    scope.launch {
                                        tapWeight.animateTo(
                                            200f,
                                            spring(stiffness = 1000f, dampingRatio = 0.65f),
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
                                if (!isDouble) {
                                    scope.launch {
                                        tapWeight.animateTo(
                                            700f,
                                            spring(stiffness = 500f, dampingRatio = 0.4f),
                                        )
                                    }
                                }
                            }
                        )
                    }
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
            // Press thins the number (variable-font weight morph) like the
            // Android 16 lock-screen clock; it stays thin through a hold and
            // springs back to bold on release.
            val numberWeight = tapWeight.value.roundToInt()
            val heroStyle = MaterialTheme.typography.displayLarge.copy(
                fontFamily = heroNumberFontFamily(fontKey, numberWeight),
                fontWeight = FontWeight(numberWeight.coerceIn(1, 1000)),
            )
            Row(
                modifier = Modifier
                    .graphicsLayer {
                        val s = scaleAnim.value
                        scaleX = s
                        scaleY = s
                        // Liquify (API 33+): rebuild the RenderEffect every frame so the
                        // animated time/progress uniforms take effect (uniforms of an
                        // already-applied effect can't be mutated). Reading warpProgress.value
                        // and warpTime here registers this lambda as a per-frame snapshot
                        // reader. The card-root clip keeps any melt overflow inside the border.
                        if (liquifyShader != null && warpProgress.value > 0f) {
                            liquifyShader.setFloatUniform("size", size.width, size.height)
                            liquifyShader.setFloatUniform("time", warpTime)
                            liquifyShader.setFloatUniform("progress", warpProgress.value)
                            renderEffect = RenderEffect
                                .createRuntimeShaderEffect(liquifyShader, "content")
                                .asComposeRenderEffect()
                        } else {
                            renderEffect = null
                        }
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
                        style = heroStyle,
                        color = onSurface,
                    )
                }
                Text(
                    text = "W",
                    style = heroStyle,
                    color = onSurface,
                )
            }
            }
            if (showChargeLevel) {
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
