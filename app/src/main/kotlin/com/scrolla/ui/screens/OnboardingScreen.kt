package com.scrolla.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.os.Build
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrolla.ui.components.ScrollaPrimaryButton
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.scrolla.ui.components.ScrollaCard
import com.scrolla.ui.components.SectionLabel
import com.scrolla.ui.theme.PillShape
import com.scrolla.ui.theme.ScrollaType
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.scrollaColors
import com.scrolla.ui.theme.spacing
import com.scrolla.device.BatteryWhitelistHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────
// Onboarding phases — three emotional beats
// ─────────────────────────────────────────────

private enum class OnboardingPhase {
    QUESTION,   // The user commits to a guess
    REVEAL,     // The truth creates surprise or validation
    INVITATION, // The social mechanic resolves into action
    PERMISSION, // Trust gate
    BATTERY_WHITELIST, // Ensure device doesn't kill the tracker
    JOIN_GROUP  // Social commitment
}

private enum class GuessOption(val label: String) {
    FEW_CENTIMETRES(ScrollaStrings.ONBOARDING_GUESS_FEW),
    ABOUT_A_METRE(ScrollaStrings.ONBOARDING_GUESS_METRE),
    SEVERAL_KILOMETRES(ScrollaStrings.ONBOARDING_GUESS_KM)
}

// ─────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit = {},
    onGrantPermission: () -> Unit = {}
) {
    var phase by remember { mutableStateOf(OnboardingPhase.QUESTION) }
    var selectedGuess by remember { mutableStateOf<GuessOption?>(null) }
    val scope = rememberCoroutineScope()

    val phaseIndex = phase.ordinal

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary

    // Phase-dependent ambient glow.
    //
    // The animated travel between phases is worth keeping — it is the one
    // thing tying the six screens together. What changed is where it sits
    // and how bright it gets.
    //
    // Every anchor is now BELOW the type. The glow used to pool at
    // y = 0.10–0.40, which is exactly where each screen's headline and
    // figure live, so coral text was being asked to read against coral
    // light. Anchoring it low means the light comes from underneath the
    // content — on Reveal, from the foot of the bar being measured.
    val glowCenterX by animateFloatAsState(
        targetValue = when (phase) {
            OnboardingPhase.QUESTION -> 0.26f
            OnboardingPhase.REVEAL -> 0.44f
            OnboardingPhase.INVITATION -> 0.72f
            OnboardingPhase.PERMISSION -> 0.50f
            OnboardingPhase.BATTERY_WHITELIST -> 0.35f
            OnboardingPhase.JOIN_GROUP -> 0.50f
        },
        animationSpec = tween(800),
        label = "glow_x"
    )
    val glowCenterY by animateFloatAsState(
        targetValue = when (phase) {
            OnboardingPhase.QUESTION -> 0.72f
            OnboardingPhase.REVEAL -> 0.66f
            OnboardingPhase.INVITATION -> 0.62f
            OnboardingPhase.PERMISSION -> 1.00f
            OnboardingPhase.BATTERY_WHITELIST -> 0.80f
            OnboardingPhase.JOIN_GROUP -> 0.70f
        },
        animationSpec = tween(800),
        label = "glow_y"
    )
    // Ceiling of 0.24. Reveal previously ran 0.18 here AND drew a second
    // radial inside RevealPhase whose centre colour was full-strength
    // primary — a combined peak around 0.33 sitting under the number.
    // That second radial is gone; this is the only glow now.
    val glowAlpha by animateFloatAsState(
        targetValue = when (phase) {
            OnboardingPhase.QUESTION -> if (isDark) 0.15f else 0.10f
            OnboardingPhase.REVEAL -> if (isDark) 0.24f else 0.14f
            OnboardingPhase.INVITATION -> if (isDark) 0.15f else 0.10f
            OnboardingPhase.PERMISSION -> if (isDark) 0.13f else 0.09f
            OnboardingPhase.BATTERY_WHITELIST -> if (isDark) 0.13f else 0.09f
            OnboardingPhase.JOIN_GROUP -> if (isDark) 0.15f else 0.10f
        },
        animationSpec = tween(800),
        label = "glow_alpha"
    )
    val glowEdgeAlpha by animateFloatAsState(
        targetValue = if (isDark) 0f else when (phase) {
            OnboardingPhase.QUESTION -> 0.03f
            OnboardingPhase.REVEAL -> 0.06f
            OnboardingPhase.INVITATION -> 0.03f
            OnboardingPhase.PERMISSION -> 0.02f
            OnboardingPhase.BATTERY_WHITELIST -> 0.03f
            OnboardingPhase.JOIN_GROUP -> 0.04f
        },
        animationSpec = tween(800),
        label = "glow_edge"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ── ROOT-LEVEL AMBIENT GLOW ──
            // Renders edge-to-edge BEHIND system bars.
            // Animates smoothly between phase-specific positions.
            val groundColor = MaterialTheme.colorScheme.background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = glowAlpha),
                                    primaryColor.copy(alpha = glowEdgeAlpha)
                                ),
                                center = Offset(size.width * glowCenterX, size.height * glowCenterY),
                                // Wide radius, gentle falloff. At 1.1x the light
                                // died just below the chart and the verdict line
                                // and button sat in an abrupt dark band. The pool
                                // now reaches the bottom edge at low alpha rather
                                // than stopping.
                                radius = if (isDark) (size.width * 1.8f) else (size.width * 2.4f)
                            )
                        )

                        // Scrim over the top third, painted in the ground colour.
                        // Glow below, type above: this is what keeps the headline
                        // and the coral figure on a clean ground instead of on
                        // more coral. Works in both themes because it tints
                        // toward whatever the background already is.
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to groundColor.copy(alpha = 0.82f),
                                    0.28f to groundColor.copy(alpha = 0.34f),
                                    0.54f to groundColor.copy(alpha = 0f)
                                )
                            )
                        )
                    }
            )

            // Content Column — respects system bar padding
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Continuous progress indicator — the one thread that connects all three phases
                OnboardingProgressTrack(
                    currentPage = phaseIndex,
                    pageCount = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.spacing.large)
                        .padding(
                            top = MaterialTheme.spacing.large,
                            bottom = MaterialTheme.spacing.small
                        )
                )

                // Main content — each phase owns its own layout
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = phase,
                        transitionSpec = {
                            when (targetState) {
                                OnboardingPhase.REVEAL ->
                                    fadeIn(tween(500, delayMillis = 200)) togetherWith
                                            fadeOut(tween(250))

                                OnboardingPhase.INVITATION, OnboardingPhase.PERMISSION, 
                                OnboardingPhase.BATTERY_WHITELIST, OnboardingPhase.JOIN_GROUP ->
                                    (slideInHorizontally { it / 5 } + fadeIn(tween(400))) togetherWith
                                            (slideOutHorizontally { -it / 5 } + fadeOut(tween(300)))

                                else -> fadeIn() togetherWith fadeOut()
                            }
                        },
                        label = "onboarding_content"
                    ) { targetPhase ->
                        when (targetPhase) {
                            OnboardingPhase.QUESTION -> QuestionPhase(
                                selectedGuess = selectedGuess,
                                onGuessSelected = { guess ->
                                    selectedGuess = guess
                                    scope.launch {
                                        delay(450) // Brief pause — acknowledge the choice
                                        phase = OnboardingPhase.REVEAL
                                    }
                                }
                            )

                            OnboardingPhase.REVEAL -> RevealPhase(
                                selectedGuess = selectedGuess,
                                onNext = { phase = OnboardingPhase.INVITATION }
                            )

                            OnboardingPhase.INVITATION -> InvitationPhase(
                                onNext = { phase = OnboardingPhase.PERMISSION }
                            )
                            
                            OnboardingPhase.PERMISSION -> PermissionPhase(
                                onLaunchSettings = {
                                    onGrantPermission()
                                },
                                onNext = { phase = OnboardingPhase.BATTERY_WHITELIST }
                            )

                            OnboardingPhase.BATTERY_WHITELIST -> BatteryWhitelistPhase(
                                onNext = { phase = OnboardingPhase.JOIN_GROUP }
                            )
                            
                            OnboardingPhase.JOIN_GROUP -> JoinGroupPhase(
                                onFinish = onFinishOnboarding
                            )
                        }
                    }
                }

            }
        }
    }
}

// ─────────────────────────────────────────────
// Progress Track
// ─────────────────────────────────────────────

@Composable
private fun OnboardingProgressTrack(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    val targetProgress = (currentPage + 1).toFloat() / pageCount
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "progress_track"
    )

    Box(
        modifier = modifier
            .height(3.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

/**
 * The body region every phase shares.
 *
 * Two things all six needed and none of them had.
 *
 * It SCROLLS, so a long manufacturer step list, an expanded "why" panel or a
 * large font scale can never put the action out of reach — onboarding used to
 * dead-end that way with no way back.
 *
 * And when the content is shorter than the viewport it CENTRES instead of
 * pinning to the top above a weighted void. Every phase previously ended with
 * Spacer(weight(1f)) — and Reveal with an explicit 0.25/0.75 split — which is
 * what produced the lopsided hole under the content.
 */
@Composable
private fun ColumnScope.OnboardingBody(
    alignment: Alignment = Alignment.Center,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .align(alignment)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

// ─────────────────────────────────────────────
// Phase 1: The Question
// ─────────────────────────────────────────────
// The user commits to a position. This is the inversion:
// instead of telling them what the product does,
// we ask them what they believe. Their guess creates
// personal stakes for the reveal.
// ─────────────────────────────────────────────

@Composable
private fun QuestionPhase(
    selectedGuess: GuessOption?,
    onGuessSelected: (GuessOption) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.spacing.large)
        ) {
            OnboardingBody(alignment = Alignment.TopStart) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = ScrollaStrings.ONBOARDING_QUESTION,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )

                Spacer(modifier = Modifier.height(40.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GuessOption.values().forEach { option ->
                        GuessOptionCard(
                            option = option,
                            isSelected = selectedGuess == option,
                            onClick = {
                                if (selectedGuess == null) { // Single selection only
                                    onGuessSelected(option)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuessOptionCard(
    option: GuessOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile press feedback — simulates a physical push
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(100),
        label = "option_press"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        animationSpec = tween(200),
        label = "option_border"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) primary.copy(alpha = 0.07f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), // Frosted glass effect
        animationSpec = tween(200),
        label = "option_bg"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) primary
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(200),
        label = "option_text"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(
                interactionSource = interactionSource,
                // Ripple restored. A 3% scale on its own is below the
                // threshold most people notice, so a slow tap read as
                // no response at all.
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = option.label,
            style = ScrollaType.Body.copy(
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            ),
            color = textColor
        )

        // A choice this consequential should look chosen. Previously the
        // only feedback was a border tint, on a screen that advances 450ms
        // later — easy to miss entirely.
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 5.dp else 1.dp,
                    color = if (isSelected) primary else MaterialTheme.scrollaColors.textFaint,
                    shape = CircleShape
                )
        )
    }
}

// ─────────────────────────────────────────────
// Phase 2: The Reveal
// ─────────────────────────────────────────────
// The emotional peak. The user's guess shrinks to a
// muted label. The real number springs in — the only
// moment in the entire onboarding where coral appears
// on a data element. The context line delivers the
// punchline: "With your thumb."
//
// This is not an illustration of the product.
// This IS the product's core insight, experienced.
// ─────────────────────────────────────────────

@Composable
private fun RevealPhase(
    selectedGuess: GuessOption?,
    onNext: () -> Unit
) {
    // Orchestrated animation sequence — three narrative beats:
    // 1. Guess callback ("You guessed: ...")
    // 2. The truth (number springs in)
    // 3. The punchline (context fades in)
    var guessLabelVisible by remember { mutableStateOf(false) }
    var numberRevealed by remember { mutableStateOf(false) }
    var contextRevealed by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200) // Beat 1: guess callback
        guessLabelVisible = true
        delay(400) // Beat 2: the truth
        numberRevealed = true
        delay(800) // Beat 3: the punchline
        contextRevealed = true
        delay(400) // Button reveal
        showButton = true
    }
    
    val buttonAlpha by animateFloatAsState(
        targetValue = if (showButton) 1f else 0f,
        animationSpec = tween(400),
        label = "button_alpha"
    )

    val guessLabelAlpha by animateFloatAsState(
        targetValue = if (guessLabelVisible) 1f else 0f,
        animationSpec = tween(400),
        label = "guess_label_alpha"
    )

    // The signature animation: spring scale with slight overshoot
    val numberScale by animateFloatAsState(
        targetValue = if (numberRevealed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessLow
        ),
        label = "number_scale"
    )

    val numberAlpha by animateFloatAsState(
        targetValue = if (numberRevealed) 1f else 0f,
        animationSpec = tween(400),
        label = "number_alpha"
    )

    val contextAlpha by animateFloatAsState(
        targetValue = if (contextRevealed) 1f else 0f,
        animationSpec = tween(600),
        label = "context_alpha"
    )

    // Beat 4: the bars grow, once the punchline has landed.
    val barProgress by animateFloatAsState(
        targetValue = if (contextRevealed) 1f else 0f,
        animationSpec = tween(900),
        label = "bar_progress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // The second radial that used to live here is gone. Its centre colour
        // was full-strength primary, and it sat directly under the number —
        // stacked on the root glow it peaked around 0.33 of coral behind
        // coral text, which is what turned the screen brown. The root glow in
        // OnboardingScreen is now the only one, anchored below the type.

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.Start
        ) {
            // Top-aligned rather than centred: this screen is dense enough
            // that centring would only shift it a few dp, and the reveal
            // reads better anchored under the progress track.
            OnboardingBody(alignment = Alignment.TopStart) {
            Spacer(modifier = Modifier.height(20.dp))

            // ── CONTENT GROUP ──
            if (selectedGuess != null) {
                Box(
                    modifier = Modifier
                        .alpha(guessLabelAlpha)
                        .clip(PillShape)
                        .border(1.dp, MaterialTheme.scrollaColors.cardBorder, PillShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "You guessed: ${selectedGuess.label.lowercase()}",
                        style = ScrollaType.Caption,
                        color = MaterialTheme.scrollaColors.textLow
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── THE NUMBER ──
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.graphicsLayer {
                    scaleX = numberScale
                    scaleY = numberScale
                    alpha = numberAlpha
                    transformOrigin = TransformOrigin(0f, 1f)
                }
            ) {
                Text(
                    text = ScrollaStrings.ONBOARDING_REVEAL_NUMBER,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 112.sp,
                        // lineHeight MUST travel with fontSize — the inherited
                        // 50sp would clip a 112sp glyph.
                        lineHeight = 112.sp,
                        letterSpacing = (-4).sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ScrollaStrings.ONBOARDING_REVEAL_UNIT,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.alignByBaseline()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── THE PUNCHLINE ──
            // The 3dp coral rule that used to sit beside this is gone. The
            // two weights already carry the hierarchy.
            Column(modifier = Modifier.alpha(contextAlpha)) {
                Text(
                    text = ScrollaStrings.ONBOARDING_REVEAL_CONTEXT_1,
                    style = ScrollaType.Row,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = ScrollaStrings.ONBOARDING_REVEAL_CONTEXT_2,
                    style = ScrollaType.Row.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // ── THE COMPARISON ──
            // Two bars, side by side, which is the version that worked.
            Spacer(modifier = Modifier.height(36.dp))

            GuessComparison(
                guess = selectedGuess,
                progress = barProgress,
                modifier = Modifier.alpha(contextAlpha)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = verdictFor(selectedGuess),
                style = ScrollaType.Editorial,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(contextAlpha)
            )

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
            }

            ScrollaPrimaryButton(
                text = ScrollaStrings.ONBOARDING_ACTION_NEXT,
                onClick = onNext,
                enabled = showButton,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(buttonAlpha)
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        }
    }
}

/**
 * How far off the user was, in their own terms.
 *
 * The guess was previously collected, shown once in a grey chip and then
 * discarded. Being wrong by three orders of magnitude is the single most
 * persuasive fact in the flow, so it is now computed and stated.
 *
 * `metres` drives the comparison bar; `SEVERAL_KILOMETRES` deliberately
 * gets a near-equal bar and a different verdict, because that guess is
 * broadly right and telling someone they were wrong when they were not
 * would burn the trust the rest of onboarding is trying to build.
 */
/**
 * The root of the contradiction: every answer on the question screen is a
 * RANGE, and a bar chart needs a POINT. Two bars side by side always assert
 * a specific guessed value, so "several kilometres" became 3 km and the
 * screen argued against a number the user never picked.
 *
 * The fix is to stop drawing the guess as a quantity of its own. There is one
 * bar — the measured distance — and the guess is a MARKER laid across it at
 * the height the user claimed. A marker can sit at the floor, or at the top,
 * without ever naming a value.
 *
 * [markerFraction] is where that line falls on the real bar, so it stays
 * truthful for all three: the two small answers land on the baseline, which
 * is the honest picture of a metre next to 2.8 km, and "several kilometres"
 * lands at the top, which reads as agreement rather than rebuttal.
 */
/**
 * The verdict, as three lines of type.
 *
 * No diagram. Two attempts at one failed for the same reason: a chart of a
 * single measured value has nothing to compare against, and any second bar
 * has to invent a quantity the user never gave. Set as type it needs no
 * geometry, states nothing false, and behaves identically on all three
 * answers — only the size changes, because a numeral carries at 92sp and a
 * word does not.
 */
private data class GuessFacts(
    /** Bar height as a fraction of the measured distance. */
    val barFraction: Float,
    /** The guess in the USER'S words. Never a number they did not give. */
    val label: String
)

private fun factsFor(guess: GuessOption?): GuessFacts? = when (guess) {
    GuessOption.FEW_CENTIMETRES -> GuessFacts(0f, "A few cm")
    GuessOption.ABOUT_A_METRE -> GuessFacts(1f / ACTUAL_METRES, "About 1 m")
    GuessOption.SEVERAL_KILOMETRES -> GuessFacts(1f, "Several km")
    null -> null
}

private fun verdictFor(guess: GuessOption?): String = when (guess) {
    // A range, so a magnitude rather than a figure.
    GuessOption.FEW_CENTIMETRES -> "Thousands of times further than you guessed."
    // The only answer that states a quantity, so the only one that earns a
    // precise multiple.
    GuessOption.ABOUT_A_METRE -> "About 2,800 times further than you guessed."
    GuessOption.SEVERAL_KILOMETRES -> "You guessed right. Almost nobody does."
    null -> "Further than almost anyone guesses."
}

private const val ACTUAL_METRES = 2800f
private val MAX_BAR_HEIGHT = 182.dp

/**
 * The comparison, restored to two bars.
 *
 * The contradiction — every answer is a RANGE, but a bar needs a POINT — is
 * solved in how the bars are DRAWN rather than by deleting one of them:
 *
 *   the measurement is solid, with a hard top edge
 *   the guess FADES OUT at the top, because it was never a precise claim
 *
 * So "several kilometres" reads as "somewhere around here", not as an
 * assertion of 3 km, and the label stays in the user's own words.
 */
@Composable
private fun GuessComparison(
    guess: GuessOption?,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val facts = factsFor(guess) ?: return
    val colors = MaterialTheme.scrollaColors
    val primary = MaterialTheme.colorScheme.primary
    val columnGap = 28.dp

    val guessHeight = (MAX_BAR_HEIGHT * facts.barFraction.coerceIn(0f, 1f) * progress)
        .coerceAtLeast(2.dp)
    val actualHeight = MAX_BAR_HEIGHT * progress

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "You guessed ${facts.label}. A typical day is " +
                "${ScrollaStrings.ONBOARDING_REVEAL_NUMBER} kilometres."
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MAX_BAR_HEIGHT + 54.dp)
                .drawBehind {
                    drawRect(
                        color = primary.copy(alpha = 0.22f),
                        topLeft = Offset(0f, size.height - 1.dp.toPx()),
                        size = Size(size.width, 1.dp.toPx())
                    )
                },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(columnGap)
        ) {
            ComparisonColumn(
                modifier = Modifier.weight(1f),
                valueText = facts.label,
                valueStyle = ScrollaType.FigureSmall.copy(fontSize = 20.sp, lineHeight = 22.sp),
                valueColor = colors.textLow,
                barHeight = guessHeight,
                // Transparent at the top: an approximation, not a reading.
                barBrush = Brush.verticalGradient(
                    listOf(colors.textFaint.copy(alpha = 0f), colors.textFaint)
                )
            )
            ComparisonColumn(
                modifier = Modifier.weight(1f),
                valueText = "${ScrollaStrings.ONBOARDING_REVEAL_NUMBER} ${ScrollaStrings.ONBOARDING_REVEAL_UNIT}",
                valueStyle = ScrollaType.FigureSmall.copy(fontSize = 26.sp, lineHeight = 28.sp),
                valueColor = primary,
                barHeight = actualHeight,
                barBrush = Brush.verticalGradient(
                    listOf(primary.copy(alpha = 0.86f), primary)
                )
            )
        }

        // Category labels sit UNDER their own bar, below the baseline.
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(columnGap)
        ) {
            Text(
                text = "YOUR GUESS",
                style = ScrollaType.Micro,
                color = colors.textLow,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "A TYPICAL DAY",
                style = ScrollaType.Micro,
                color = primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ComparisonColumn(
    valueText: String,
    valueStyle: TextStyle,
    valueColor: Color,
    barHeight: Dp,
    barBrush: Brush,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)
    ) {
        Text(
            text = valueText,
            style = valueStyle,
            color = valueColor,
            textAlign = TextAlign.Center
        )
        // No per-bar bloom. That drew a radial into a fixed 3x-wide rect,
        // which clipped into a visible rectangle with hard edges under the
        // baseline. The ambient glow is anchored here instead.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .height(barHeight)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(barBrush)
        )
    }
}

// ─────────────────────────────────────────────
// Phase 3: The Invitation
// ─────────────────────────────────────────────
// Resolution. The emotional peak has passed. Now
// the social mechanic is introduced quietly, grounded
// in the visceral number the user just experienced.
// A miniature leaderboard visualization anchors
// the concept visually. The CTA closes the arc.
// ─────────────────────────────────────────────

@Composable
private fun InvitationPhase(
    onNext: () -> Unit
) {
    var animationTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        animationTrigger = true
    }

    // Staggered entry for the leaderboard preview — each row slides up into place
    val row1Progress by animateFloatAsState(
        if (animationTrigger) 1f else 0f,
        tween(600, delayMillis = 200),
        label = "lb_row1"
    )
    val row2Progress by animateFloatAsState(
        if (animationTrigger) 1f else 0f,
        tween(600, delayMillis = 350),
        label = "lb_row2"
    )
    val row3Progress by animateFloatAsState(
        if (animationTrigger) 1f else 0f,
        tween(600, delayMillis = 500),
        label = "lb_row3"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.Start
        ) {
            // Top-aligned. Centring pushed the headline into the middle of
            // the screen and it read as floating; this phase is a statement
            // followed by evidence, and a statement belongs at the top.
            OnboardingBody(alignment = Alignment.TopStart) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = ScrollaStrings.ONBOARDING_INVITE_HEADLINE,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                Text(
                    text = ScrollaStrings.ONBOARDING_INVITE_BODY,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Miniature reverse leaderboard — demonstrates the mechanic
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LeaderboardPreviewRow(
                        rank = 1, name = "You", distance = "1.2 km",
                        isUser = true, progress = row1Progress
                    )
                    LeaderboardPreviewRow(
                        rank = 2, name = "Alex", distance = "3.4 km",
                        isUser = false, progress = row2Progress
                    )
                    LeaderboardPreviewRow(
                        rank = 3, name = "Sam", distance = "5.8 km",
                        isUser = false, progress = row3Progress
                    )
                }
            }

            ScrollaPrimaryButton(
                text = ScrollaStrings.ONBOARDING_ACTION_NEXT,
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        }
    }
}

@Composable
private fun LeaderboardPreviewRow(
    rank: Int,
    name: String,
    distance: String,
    isUser: Boolean,
    progress: Float
) {
    val textColor = if (isUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurface
    val distanceColor = if (isUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    val weight = if (isUser) FontWeight.SemiBold else FontWeight.Normal

    // Slides up into place while fading in — visual metaphor for ranking
    val offsetY = (1f - progress) * 16f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = offsetY * density
            }
            .then(
                if (isUser) Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = weight),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = weight),
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = distance,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = weight),
            color = distanceColor
        )
    }
}

// ─────────────────────────────────────────────
// Phase 4: Permission
// ─────────────────────────────────────────────

@Composable
private fun PermissionPhase(
    onLaunchSettings: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasLaunchedSettings by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (com.scrolla.device.isScrollAccessibilityServiceEnabled(context)) {
                    onNext()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var animationTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        animationTrigger = true
    }

    val alpha1 by animateFloatAsState(if (animationTrigger) 1f else 0f, tween(500, delayMillis = 100), label = "alpha1")
    val alpha2 by animateFloatAsState(if (animationTrigger) 1f else 0f, tween(500, delayMillis = 300), label = "alpha2")
    val alpha3 by animateFloatAsState(if (animationTrigger) 1f else 0f, tween(500, delayMillis = 500), label = "alpha3")

    var showWhy by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.Start
        ) {
            // A headline, a subhead and nine bullets. At the 2.0 font scale
            // this file's own preview declares, the button used to be
            // unreachable and onboarding dead-ended right here.
            OnboardingBody(alignment = Alignment.TopStart) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = ScrollaStrings.PERMISSION_HEADLINE,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alpha(alpha1)
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            Text(
                text = ScrollaStrings.PERMISSION_SUBHEADLINE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(alpha1)
            )

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

            // Three bordered cards, colour-coded by what each group means:
            // green reads, amber never, neutral where it goes. The colour is
            // the fastest way to answer the only question anyone actually has
            // on a permission screen.
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.cardGap)) {
                PermissionCard(
                    label = ScrollaStrings.PERMISSION_TRACK_HEADER,
                    accent = MaterialTheme.scrollaColors.improving,
                    icon = Icons.Default.Check,
                    bullets = listOf(
                        ScrollaStrings.PERMISSION_TRACK_1,
                        ScrollaStrings.PERMISSION_TRACK_2,
                        ScrollaStrings.PERMISSION_TRACK_3
                    ),
                    modifier = Modifier.alpha(alpha2)
                )
                PermissionCard(
                    label = ScrollaStrings.PERMISSION_NEVER_HEADER,
                    accent = MaterialTheme.scrollaColors.worsening,
                    icon = Icons.Default.Close,
                    bullets = listOf(
                        ScrollaStrings.PERMISSION_NEVER_1,
                        ScrollaStrings.PERMISSION_NEVER_2,
                        ScrollaStrings.PERMISSION_NEVER_3
                    ),
                    modifier = Modifier.alpha(alpha2)
                )
                PermissionCard(
                    label = ScrollaStrings.PERMISSION_DATA_HEADER,
                    accent = MaterialTheme.scrollaColors.textLow,
                    icon = Icons.Default.Lock,
                    bullets = listOf(
                        ScrollaStrings.PERMISSION_DATA_1,
                        ScrollaStrings.PERMISSION_DATA_2,
                        ScrollaStrings.PERMISSION_DATA_3
                    ),
                    modifier = Modifier.alpha(alpha3)
                )
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))

            // Why link — inside the scroll, so expanding it can never push
            // the action off the bottom of the screen.
            TextButton(
                onClick = { showWhy = !showWhy },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .alpha(alpha3)
            ) {
                Text(
                    text = ScrollaStrings.PERMISSION_WHY_LINK,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showWhy) {
                Text(
                    text = ScrollaStrings.PERMISSION_WHY_BODY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(alpha3)
                )
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
            }

            // Pinned action — always reachable, whatever the font scale.
            ScrollaPrimaryButton(
                text = if (com.scrolla.device.isScrollAccessibilityServiceEnabled(context)) "Permission granted — continue" else ScrollaStrings.PERMISSION_BUTTON,
                onClick = {
                    if (com.scrolla.device.isScrollAccessibilityServiceEnabled(context)) {
                        onNext()
                    } else {
                        hasLaunchedSettings = true
                        onLaunchSettings()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha3)
            )

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        }
    }
}

/**
 * One group of permission facts.
 *
 * Each bullet leads with its icon so the rows anchor to a common left edge
 * and the text runs the full width of the card — the mockup's rows were
 * plain text starting at the padding, which left a ragged column of dead
 * space down the right of every card.
 */
@Composable
private fun PermissionCard(
    label: String,
    accent: Color,
    icon: ImageVector,
    bullets: List<String>,
    modifier: Modifier = Modifier
) {
    ScrollaCard(modifier = modifier, padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(label.removeSuffix(":"), color = accent)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                bullets.forEach { bullet ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp),
                            tint = accent.copy(alpha = 0.85f)
                        )
                        Text(
                            text = bullet,
                            style = ScrollaType.Caption.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionSection(
    header: String,
    bullets: List<String>,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.padding(top = 2.dp, end = 12.dp)) {
            icon()
        }
        Column {
            Text(
                text = header,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            bullets.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Phase 4b: Battery Whitelist
// ─────────────────────────────────────────────

@Composable
private fun BatteryWhitelistPhase(
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val helper = remember { BatteryWhitelistHelper() }
    val instructions = helper.getInstructions(Build.MANUFACTURER)
    var hasLaunchedSettings by remember { mutableStateOf(false) }

    // Auto-advance only where there is a single thing to do. On an aggressive OEM
    // this screen now has several steps with a button each, and skipping ahead the
    // moment the user returns from the first one would hide the rest — including
    // the Recents lock, which is the step that actually matters (P2.15).
    val autoAdvanceOnReturn = instructions.steps.count { it.action != null } <= 1
    DisposableEffect(lifecycleOwner, autoAdvanceOnReturn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasLaunchedSettings && autoAdvanceOnReturn) {
                onNext()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var animationTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        animationTrigger = true
    }

    val alpha1 by animateFloatAsState(if (animationTrigger) 1f else 0f, tween(500, delayMillis = 100), label = "alpha1")
    val alpha2 by animateFloatAsState(if (animationTrigger) 1f else 0f, tween(500, delayMillis = 300), label = "alpha2")
    val alpha3 by animateFloatAsState(if (animationTrigger) 1f else 0f, tween(500, delayMillis = 500), label = "alpha3")

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.extraLarge),
            horizontalAlignment = Alignment.Start
        ) {
            OnboardingBody(alignment = Alignment.TopStart) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = ScrollaStrings.BATTERY_HEADLINE,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alpha(alpha1)
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            Text(
                text = ScrollaStrings.BATTERY_SUBHEADLINE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(alpha1)
            )

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))

            // Manufacturer specific steps — length varies by device, which is
            // exactly why this region has to scroll.
            Column(modifier = Modifier.alpha(alpha2)) {
                Text(
                    text = "${ScrollaStrings.BATTERY_MANUFACTURER_PREFIX} ${instructions.manufacturer}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                instructions.steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Number circle
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp, end = 12.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (step.critical) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = step.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (step.critical) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            // Each reachable step gets its own button, so the user never
                            // has to find a settings screen themselves. The critical step
                            // has none on purpose: locking an app in Recents is a gesture
                            // in the recents UI, not a screen anything can launch.
                            val action = step.action
                            if (action != null) {
                                TextButton(
                                    onClick = {
                                        hasLaunchedSettings = true
                                        helper.launch(context, action)
                                    },
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = step.actionLabel ?: ScrollaStrings.BATTERY_OPEN_SETTINGS_BUTTON,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
            }

            // With a button on every reachable step, a second "Open settings" at the
            // bottom would just be a fifth way to reach the same screens. Where the
            // steps carry their own actions this becomes the acknowledgement instead.
            if (autoAdvanceOnReturn) {
                ScrollaPrimaryButton(
                    text = ScrollaStrings.BATTERY_OPEN_SETTINGS_BUTTON,
                    onClick = {
                        hasLaunchedSettings = true
                        helper.openBatterySettings(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(alpha3)
                )
            } else {
                ScrollaPrimaryButton(
                    text = ScrollaStrings.BATTERY_DONE_BUTTON,
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(alpha3)
                )
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraExtraSmall))

            TextButton(
                onClick = onNext,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .alpha(alpha3)
            ) {
                Text(
                    text = ScrollaStrings.BATTERY_CONTINUE_BUTTON,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Phase 5: Join Group
// ─────────────────────────────────────────────

@Composable
private fun JoinGroupPhase(
    onFinish: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.extraLarge),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Top-aligned, and scrolling — this is the one phase with a text
            // field, and the keyboard takes roughly half the screen.
            OnboardingBody(alignment = Alignment.TopStart) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = ScrollaStrings.GROUP_HEADLINE,
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
                Text(
                    text = ScrollaStrings.GROUP_SUBHEADLINE,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(40.dp))

                OtpInputField(
                    code = code,
                    onCodeChange = { code = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(34.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(MaterialTheme.scrollaColors.cardBorder)
                    )
                    SectionLabel(ScrollaStrings.GROUP_NO_CODE_LABEL)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(MaterialTheme.scrollaColors.cardBorder)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // The skip path, as a real choice rather than a faint text
                // link under the button. Creating a group is deliberately not
                // here — that lives inside the app.
                ScrollaCard(onClick = onFinish) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = ScrollaStrings.GROUP_SOLO_TITLE,
                                style = ScrollaType.Body.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = ScrollaStrings.GROUP_SOLO_BODY,
                                style = ScrollaType.Caption,
                                color = MaterialTheme.scrollaColors.textLow
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.scrollaColors.textLow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
            }

            // One action at the bottom.
            ScrollaPrimaryButton(
                text = ScrollaStrings.GROUP_JOIN_BUTTON,
                onClick = onFinish, // Mock success
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
        }
    }
}

@Composable
private fun OtpInputField(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = code,
        onValueChange = {
            if (it.length <= 6) {
                onCodeChange(it.uppercase())
            }
        },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Characters
        ),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = { innerTextField ->
            Box {
                innerTextField()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(6) { index ->
                        val char = when {
                            index < code.length -> code[index].toString()
                            else -> ""
                        }
                        val isFocused = index == code.length
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.85f)
                                .clip(MaterialTheme.shapes.small)
                                .border(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = MaterialTheme.shapes.small
                                )
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    )
}

// ─────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    ScrollaUILabTheme {
        OnboardingScreen()
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OnboardingScreenDarkPreview() {
    ScrollaUILabTheme(darkTheme = true) {
        OnboardingScreen()
    }
}
