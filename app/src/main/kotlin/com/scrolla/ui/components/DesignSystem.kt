package com.scrolla.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrolla.ui.theme.CardShape
import com.scrolla.ui.theme.PillShape
import com.scrolla.ui.theme.ScrollaType
import com.scrolla.ui.theme.scrollaColors
import com.scrolla.ui.theme.spacing

/**
 * Press feedback.
 *
 * The previous version set `indication = null` — removing the ripple —
 * and replaced it with a 5% scale driven by an unbounded
 * `while (true) { awaitPointerEventScope { ... } }` loop that competed
 * with the vertical scroll it lived inside. A 5% scale on its own is
 * below the threshold most people notice, so a slow tap looked like the
 * app had not responded at all.
 *
 * Now: the ripple is back, the scale rides the same interaction source
 * the click already produces, and the gesture loop is bounded.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.97f,
    onClick: (() -> Unit)? = null
) = composed {
    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) scaleDown else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
            label = "pressScale"
        )
        // Every tappable card goes through bounceClick, so the tick lives here
        // rather than at each call site.
        val tap = rememberTapHaptic()
        this
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { tap(); onClick() }
            )
    } else {
        // Decorative use on a parent that owns the click. `awaitEachGesture`
        // is the bounded form — it yields between gestures instead of
        // spinning against the scroll container.
        var pressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (pressed) scaleDown else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
            label = "pressScale"
        )
        this
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
    }
}

/**
 * Card as a modifier — kept because eight screens still chain it.
 *
 * Two things changed. The click now lands OUTSIDE the padding, so the
 * whole visible card is the tap target rather than an inset rectangle
 * inside it. And the default padding is the one card padding (20), not
 * 24 — the app was carrying 16, 24 and 40 for the same component.
 *
 * Prefer [ScrollaCard] in new code: it makes the padding-inside,
 * click-outside order structurally impossible to get wrong.
 */
@Composable
fun Modifier.bentoCard(
    shape: Shape = CardShape,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = MaterialTheme.scrollaColors.cardBorder,
    borderWidth: Dp = 1.dp,
    padding: Dp = MaterialTheme.spacing.cardPadding,
    onClick: (() -> Unit)? = null
): Modifier = this
    .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
    .clip(shape)
    .background(backgroundColor)
    .border(borderWidth, borderColor, shape)
    .padding(padding)

/**
 * The card.
 *
 * Depth comes from the surface ladder plus a hairline, and — in light
 * only — one soft shadow. Dark themes get nothing from drop shadows, so
 * it is not drawn there.
 */
@Composable
fun ScrollaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = MaterialTheme.spacing.cardPadding,
    background: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = MaterialTheme.scrollaColors.cardBorder,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = isSystemInDarkTheme()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!dark) Modifier.shadow(
                    elevation = 10.dp,
                    shape = CardShape,
                    clip = false,
                    ambientColor = Color(0x1417150F),
                    spotColor = Color(0x1417150F)
                ) else Modifier
            )
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
            .clip(CardShape)
            .background(background)
            .border(1.dp, borderColor, CardShape)
            .padding(padding),
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/**
 * Section label. `textLow`, never the accent — painting these coral on
 * every screen is most of how the accent ended up doing ten jobs.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.scrollaColors.textLow
) {
    Text(
        text = text.uppercase(),
        style = ScrollaType.Micro,
        color = color,
        modifier = modifier
    )
}

/**
 * Direction, never brand. Down is good in this app, so painting a
 * decrease in the accent colour would read backwards.
 */
@Composable
fun DeltaChip(
    text: String,
    improving: Boolean,
    modifier: Modifier = Modifier
) {
    val tint = if (improving) {
        MaterialTheme.scrollaColors.improving
    } else {
        MaterialTheme.scrollaColors.worsening
    }
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.24f), PillShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (improving) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = tint
        )
        Text(
            text = text,
            style = ScrollaType.Caption,
            color = tint
        )
    }
}
