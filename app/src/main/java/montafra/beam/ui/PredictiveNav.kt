package montafra.beam.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

// Material 3 emphasized easings, as used by the Android 16 Settings app
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private const val PushDurationMs = 400
private const val PopDurationMs = 350
private const val CommitDurationMs = 250
private const val CancelDurationMs = 200

// Predictive back gesture: surface shrinks to 90%, stays fully opaque
private const val BackMinScale = 0.9f
private const val BackShiftFraction = 0.05f
private const val BackTouchYDamping = 0.05f
private val BackCornerRadius = 28.dp
private val BackShadowElevation = 6.dp
private const val BackScrimAlpha = 0.25f

// Forward/backward slide: bottom screen parallaxes by a quarter width under a dim scrim
private const val ParallaxFraction = 0.25f
private const val SlideScrimAlpha = 0.15f

internal class NavTransition(
    val from: String,
    val to: String,
    val isPop: Boolean,
) {
    var isGesture by mutableStateOf(false)
    var committed = false
    var swipeEdge = BackEventCompat.EDGE_LEFT
    var startTouchY = 0f
    var touchYDelta by mutableStateOf(0f)
    val progress = Animatable(0f)
    val topAlpha = Animatable(1f)

    val topRoute: String get() = if (isPop) from else to
    val bottomRoute: String get() = if (isPop) to else from

    fun applyTop(g: GraphicsLayerScope): Unit = with(g) {
        val p = progress.value
        when {
            !isPop -> translationX = (1f - p) * size.width
            isGesture -> {
                val scale = 1f - (1f - BackMinScale) * p
                scaleX = scale
                scaleY = scale
                val direction = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                translationX = direction * p * size.width * BackShiftFraction
                translationY = touchYDelta * BackTouchYDamping
                alpha = topAlpha.value
                val ramp = min(1f, p * 5f)
                shape = RoundedCornerShape(BackCornerRadius.toPx() * ramp)
                clip = true
                shadowElevation = BackShadowElevation.toPx() * ramp
            }
            else -> translationX = p * size.width
        }
    }

    fun applyBottom(g: GraphicsLayerScope): Unit = with(g) {
        val p = progress.value
        if (!isPop) {
            translationX = -p * size.width * ParallaxFraction
        } else if (!isGesture) {
            translationX = -(1f - p) * size.width * ParallaxFraction
        }
        // gesture pop: the previous screen sits in place, revealed behind the shrinking page
    }

    fun scrimAlpha(): Float {
        val p = progress.value
        return when {
            !isPop -> SlideScrimAlpha * p
            isGesture -> BackScrimAlpha * (1f - p)
            else -> SlideScrimAlpha * (1f - p)
        }
    }
}

class BeamNavController internal constructor(
    startRoute: String,
    private val scope: CoroutineScope,
) {
    internal val backStack = mutableStateListOf(startRoute)
    internal var transition by mutableStateOf<NavTransition?>(null)

    val canPop: Boolean get() = backStack.size > 1

    fun navigate(route: String) {
        if (transition != null || backStack.last() == route) return
        val t = NavTransition(from = backStack.last(), to = route, isPop = false)
        backStack.add(route)
        transition = t
        scope.launch {
            t.progress.animateTo(1f, tween(PushDurationMs, easing = EmphasizedDecelerate))
            if (transition === t) transition = null
        }
    }

    fun popBackStack() {
        if (transition != null || !canPop) return
        val t = NavTransition(from = backStack.last(), to = backStack[backStack.size - 2], isPop = true)
        transition = t
        scope.launch {
            t.progress.animateTo(1f, tween(PopDurationMs, easing = EmphasizedDecelerate))
            finishPop(t)
        }
    }

    private fun finishPop(t: NavTransition) {
        if (transition === t) {
            backStack.removeAt(backStack.size - 1)
            transition = null
        }
    }

    internal suspend fun handleBackGesture(events: Flow<BackEventCompat>) {
        // A new back gesture interrupts any in-flight transition: settle it instantly
        transition?.let { running ->
            if (running.isPop) backStack.removeAt(backStack.size - 1)
            transition = null
        }
        if (!canPop) return

        val t = NavTransition(from = backStack.last(), to = backStack[backStack.size - 2], isPop = true)
        t.isGesture = true
        var started = false
        try {
            events.collect { e ->
                if (!started) {
                    started = true
                    t.swipeEdge = e.swipeEdge
                    t.startTouchY = e.touchY
                    transition = t
                }
                t.touchYDelta = e.touchY - t.startTouchY
                t.progress.snapTo(e.progress)
            }
            // Finger lifted past the threshold (or plain back press): commit the pop
            if (!started) transition = t
            t.committed = true
            if (t.progress.value < 0.02f) {
                // No meaningful gesture (e.g. 3-button nav): play the full backward slide
                t.isGesture = false
                t.progress.animateTo(1f, tween(PopDurationMs, easing = EmphasizedDecelerate))
            } else {
                // Only now does the page fade — it stays solid for the whole gesture
                coroutineScope {
                    launch { t.progress.animateTo(1f, tween(CommitDurationMs, easing = LinearOutSlowInEasing)) }
                    launch { t.topAlpha.animateTo(0f, tween(CommitDurationMs)) }
                }
            }
            finishPop(t)
        } catch (e: CancellationException) {
            if (t.committed) {
                // Commit animation was interrupted by a new gesture: complete the pop at once
                finishPop(t)
            } else if (started) {
                // Gesture cancelled: settle the page back into place, still fully opaque
                scope.launch {
                    try {
                        t.progress.animateTo(0f, tween(CancelDurationMs, easing = LinearOutSlowInEasing))
                    } finally {
                        if (transition === t) transition = null
                    }
                }
            }
            throw e
        }
    }
}

@Composable
fun rememberBeamNavController(startRoute: String): BeamNavController {
    val scope = rememberCoroutineScope()
    return remember { BeamNavController(startRoute, scope) }
}

@Composable
fun PredictiveNavHost(
    controller: BeamNavController,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    PredictiveBackHandler(enabled = controller.canPop) { events ->
        controller.handleBackGesture(events)
    }

    val stateHolder = rememberSaveableStateHolder()
    val knownRoutes = remember { mutableSetOf<String>() }
    val stackSnapshot = controller.backStack.toList()
    LaunchedEffect(stackSnapshot) {
        // Drop saved state of routes that have left the back stack
        val alive = stackSnapshot.toSet()
        val iterator = knownRoutes.iterator()
        while (iterator.hasNext()) {
            val route = iterator.next()
            if (route !in alive) {
                stateHolder.removeState(route)
                iterator.remove()
            }
        }
    }

    val t = controller.transition
    val visible = if (t == null) listOf(controller.backStack.last()) else listOf(t.bottomRoute, t.topRoute)

    Box(modifier.fillMaxSize()) {
        for ((index, route) in visible.withIndex()) {
            val isTop = index == visible.lastIndex
            knownRoutes.add(route)
            key(route) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            controller.transition?.let { tr ->
                                if (route == tr.topRoute) tr.applyTop(this) else tr.applyBottom(this)
                            }
                        }
                ) {
                    stateHolder.SaveableStateProvider(route) { content(route) }
                    if (!isTop) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .drawBehind {
                                    val a = controller.transition?.scrimAlpha() ?: 0f
                                    if (a > 0f) drawRect(Color.Black.copy(alpha = a))
                                }
                        )
                    }
                }
            }
        }
    }
}
