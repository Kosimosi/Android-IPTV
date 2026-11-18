package com.retro.iptv

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Shared 10-foot RETRO TV focus tokens and navigation helpers. */
object RetroTvFocusKit {
    val ScreenCanvas = Color(0xFF0B0C0E)
    val CardIdleFill = Color(0xFF15171C)
    val CardIdleStroke = Color(0xFF242832)
    val CardFocusedFill = Color(0xFF241419)
    val CardFocusedStroke = Color(0xFFE11D48)
    val CardFocusGlow = Color(0x66E11D48)
    val SecondaryText = Color(0xFFA0A8B8)
    val CricketCardStroke = Color(0xFF323846)
}

data class RetroTvFocusStyle(
    val fill: Color,
    val stroke: Color,
    val strokeWidth: Dp,
    val scale: Float
)

@androidx.compose.runtime.Composable
fun rememberRetroTvFocusStyle(
    focused: Boolean,
    selected: Boolean = false,
    rapidNavigation: Boolean = false
): RetroTvFocusStyle {
    val fill by animateColorAsState(
        targetValue = when {
            focused -> RetroTvFocusKit.CardFocusedFill
            selected -> Color(0xFF1D2027)
            else -> RetroTvFocusKit.CardIdleFill
        },
        animationSpec = if (rapidNavigation) tween(65) else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "retroTvFocusFill"
    )
    val stroke by animateColorAsState(
        targetValue = if (focused) RetroTvFocusKit.CardFocusedStroke else RetroTvFocusKit.CardIdleStroke,
        animationSpec = if (rapidNavigation) tween(65) else tween(200),
        label = "retroTvFocusStroke"
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.07f else 1f,
        animationSpec = if (rapidNavigation) tween(65, easing = Easing { 1f - (1f - it) * (1f - it) })
        else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "retroTvFocusScale"
    )
    return RetroTvFocusStyle(fill, stroke, if (focused) 2.dp else 1.dp, scale)
}

/** Lift the focused Compose card above adjacent siblings without clipping its shadow. */
fun Modifier.retroTvFocusLayer(focused: Boolean, scale: Float): Modifier =
    this
        .zIndex(if (focused) 1f else 0f)
        .shadow(
            elevation = if (focused) 12.dp else 0.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            clip = false,
            ambientColor = RetroTvFocusKit.CardFocusGlow,
            spotColor = RetroTvFocusKit.CardFocusGlow
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationZ = if (focused) 12.dp.toPx() else 0f
            clip = false
        }

/** Request focus when an air-mouse enters a focusable Compose item. */
fun Modifier.retroTvHoverRequestsFocus(focusRequester: FocusRequester): Modifier =
    this.pointerInput(focusRequester) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Enter) {
                    focusRequester.requestFocus()
                }
            }
        }
    }

/**
 * Center a focused LazyColumn item. Extra vertical content padding lets the first and
 * last results reach the same center position as items in the middle.
 */
@androidx.compose.runtime.Composable
fun Modifier.retroTvCenterOnFocus(
    listState: LazyListState,
    itemIndex: Int,
    scope: CoroutineScope,
    onFocusChanged: (Boolean) -> Unit = {},
    estimatedItemHeight: Dp = 64.dp
): Modifier {
    val density = LocalDensity.current
    return this.onFocusChanged { state ->
        onFocusChanged(state.isFocused)
        if (state.isFocused) {
            scope.launch {
                val layout = listState.layoutInfo
                val viewportHeight = layout.viewportSize.height
                val itemHeight = layout.visibleItemsInfo.firstOrNull { it.index == itemIndex }?.size
                    ?: with(density) { estimatedItemHeight.roundToPx() }
            val centerOffset = ((viewportHeight - itemHeight) / 2).coerceAtLeast(0)
                listState.animateScrollToItem(itemIndex, scrollOffset = -centerOffset)
            }
        }
    }
}

/** Throttle held directional-key repeats to at most one accepted step per 95ms. */
@androidx.compose.runtime.Composable
fun rememberRetroTvDpadRepeatThrottle(
    onRapidNavigation: () -> Unit = {},
    intervalMs: Long = 95L
): Modifier {
    var lastAcceptedRepeat by remember { mutableLongStateOf(0L) }
    val currentCallback = rememberUpdatedState(onRapidNavigation)
    return Modifier.onPreviewKeyEvent { event: KeyEvent ->
        val native = event.nativeKeyEvent
        val directional = native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP ||
            native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
            native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
            native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
        if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount > 0 && directional) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAcceptedRepeat < intervalMs) {
                true
            } else {
                lastAcceptedRepeat = now
                currentCallback.value()
                false
            }
        } else false
    }
}
