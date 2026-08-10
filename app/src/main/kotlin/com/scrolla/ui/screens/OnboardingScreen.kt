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
import com.scrolla.ui.theme.ScrollaUILabTheme
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

    // Phase-dependent ambient glow parameters — each phase has unique positioning
    val glowCenterX by animateFloatAsState(
        targetValue = when (phase) {
            OnboardingPhase.QUESTION -> 0.85f
            OnboardingPhase.REVEAL -> 0.3f
            OnboardingPhase.INVITATION -> 0.75f
            OnboardingPhase.PERMISSION -> 0.5f
            OnboardingPhase.BATTERY_WHITELIST -> 0.35f
            OnboardingPhase.JOIN_GROUP -> 0.5f
        },
        animationSpec = tween(800),
        label = "glow_x"
    )
    val glowCenterY by animateFloatAsState(
        targetValue = when (phase) {
            OnboardingPhase.QUESTION -> 0.15f
            OnboardingPhase.REVEAL -> 0.40f
            OnboardingPhase.INVITATION -> 0.40f
            OnboardingPhase.PERMISSION -> 0.10f
            OnboardingPhase.BATTERY_WHITELIST -> 0.20f
            OnboardingPhase.JOIN_GROUP -> 0.28f
        },
        animationSpec = tween(800),
        label = "glow_y"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = when (phase) {
            OnboardingPhase.QUESTION -> if (isDark) 0.10f else 0.12f
            OnboardingPhase.REVEAL -> if (isDark) 0.18f else 0.20f
            OnboardingPhase.INVITATION -> if (isDark) 0.12f else 0.14f
            OnboardingPhase.PERMISSION -> if (isDark) 0.08f else 0.10f
            OnboardingPhase.BATTERY_WHITELIST -> if (isDark) 0.10f else 0.12f
            OnboardingPhase.JOIN_GROUP -> if (isDark) 0.12f else 0.15f
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
                                radius = if (isDark) (size.width * 1.0f) else (size.width * 1.8f)
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
                                onGrantPermission = {
                                    onGrantPermission()
                                    phase = OnboardingPhase.BATTERY_WHITELIST
                                }
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
            Spacer(modifier = Modifier.height(32.dp))

            // The question — large, confident, left-aligned
            Text(
                text = ScrollaStrings.ONBOARDING_QUESTION,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 44.sp,
                    letterSpacing = (-1.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Three guess options — the user's commitment
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
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

            Spacer(modifier = Modifier.weight(1f))
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

    Box(
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
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = textColor
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

    Box(modifier = Modifier.fillMaxSize()) {
        // ── AMBIENT BACKGROUND GLOW ──
        // The emotional crescendo. In dark mode, a focused spotlight blooms behind the number.
        // In light mode, a warm, expansive glow floods the entire screen — the number IS the sun.
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val primaryColor = MaterialTheme.colorScheme.primary
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 0.8f + (numberScale * 0.4f)
                    scaleY = 0.8f + (numberScale * 0.4f)
                    alpha = numberAlpha * if (isDark) 0.15f else 0.35f
                }
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor,
                                primaryColor.copy(alpha = if (isDark) 0f else 0.12f)
                            ),
                            center = Offset(size.width * 0.3f, size.height * 0.45f),
                            radius = if (isDark) (size.width * 0.8f) else (size.width * 1.6f)
                        )
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.Start
        ) {
            // Position the content group at optical center (above geometric center)
            Spacer(modifier = Modifier.weight(0.25f))

            // ── CONTENT GROUP ──
            if (selectedGuess != null) {
                Box(
                    modifier = Modifier
                        .alpha(guessLabelAlpha)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "You guessed: ${selectedGuess.label}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
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
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-4).sp
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

            Spacer(modifier = Modifier.height(32.dp))

            // ── THE PUNCHLINE ──
            Row(
                modifier = Modifier
                    .alpha(contextAlpha)
                    .fillMaxWidth(0.9f)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(52.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = ScrollaStrings.ONBOARDING_REVEAL_CONTEXT_1,
                        style = MaterialTheme.typography.titleLarge.copy(
                            lineHeight = 28.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ScrollaStrings.ONBOARDING_REVEAL_CONTEXT_2,
                        style = MaterialTheme.typography.titleLarge.copy(
                            lineHeight = 28.sp,
                            letterSpacing = (-0.5).sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Remaining space below — content breathes upward
            Spacer(modifier = Modifier.weight(0.75f))

            ScrollaPrimaryButton(
                text = ScrollaStrings.ONBOARDING_ACTION_NEXT,
                onClick = onNext,
                enabled = showButton,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(buttonAlpha)
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
        }
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
            Spacer(modifier = Modifier.height(32.dp))

            // Typography spine — grounded, not grand
            Text(
                text = ScrollaStrings.ONBOARDING_INVITE_HEADLINE,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 44.sp,
                    letterSpacing = (-1.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
            Text(
                text = ScrollaStrings.ONBOARDING_INVITE_BODY,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp,
                    letterSpacing = 0.sp
                ),
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

            Spacer(modifier = Modifier.weight(1f))
            
            ScrollaPrimaryButton(
                text = ScrollaStrings.ONBOARDING_ACTION_NEXT,
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
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
    onGrantPermission: () -> Unit
) {
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
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = ScrollaStrings.PERMISSION_HEADLINE,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.alpha(alpha1)
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            Text(
                text = ScrollaStrings.PERMISSION_SUBHEADLINE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(alpha1)
            )

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))

            // Section 1
            PermissionSection(
                header = ScrollaStrings.PERMISSION_TRACK_HEADER,
                bullets = listOf(ScrollaStrings.PERMISSION_TRACK_1, ScrollaStrings.PERMISSION_TRACK_2, ScrollaStrings.PERMISSION_TRACK_3),
                icon = { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.alpha(alpha2)
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))
            
            // Section 2
            PermissionSection(
                header = ScrollaStrings.PERMISSION_NEVER_HEADER,
                bullets = listOf(ScrollaStrings.PERMISSION_NEVER_1, ScrollaStrings.PERMISSION_NEVER_2, ScrollaStrings.PERMISSION_NEVER_3),
                icon = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.alpha(alpha2)
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

            // Section 3
            PermissionSection(
                header = ScrollaStrings.PERMISSION_DATA_HEADER,
                bullets = listOf(ScrollaStrings.PERMISSION_DATA_1, ScrollaStrings.PERMISSION_DATA_2, ScrollaStrings.PERMISSION_DATA_3),
                icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                modifier = Modifier.alpha(alpha3)
            )

            Spacer(modifier = Modifier.weight(1f))

            ScrollaPrimaryButton(
                text = ScrollaStrings.PERMISSION_BUTTON,
                onClick = onGrantPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha3)
            )
            
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            
            // Why link
            var showWhy by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showWhy = !showWhy },
                modifier = Modifier.align(Alignment.CenterHorizontally).alpha(alpha3)
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
                    modifier = Modifier.padding(top = MaterialTheme.spacing.small).alpha(alpha3)
                )
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
    val helper = remember { BatteryWhitelistHelper() }
    val instructions = helper.getInstructions(Build.MANUFACTURER)

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
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = ScrollaStrings.BATTERY_HEADLINE,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
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

            // Manufacturer specific steps
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
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Number circle
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp, end = 12.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            ScrollaPrimaryButton(
                text = ScrollaStrings.BATTERY_OPEN_SETTINGS_BUTTON,
                onClick = { helper.openBatterySettings(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha3)
            )
            
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))

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
            
            Text(
                text = ScrollaStrings.GROUP_HEADLINE,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            Text(
                text = ScrollaStrings.GROUP_SUBHEADLINE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // OTP Content
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OtpInputField(
                    code = code,
                    onCodeChange = { code = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            ScrollaPrimaryButton(
                text = ScrollaStrings.GROUP_JOIN_BUTTON,
                onClick = onFinish, // Mock success
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
            
            TextButton(
                onClick = onFinish,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = ScrollaStrings.GROUP_SKIP,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
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
