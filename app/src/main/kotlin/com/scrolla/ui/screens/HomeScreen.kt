package com.scrolla.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.spacing
import com.scrolla.ui.components.bentoCard
import com.scrolla.ui.components.bounceClick

/**
 * Screen 5 — Home
 *
 * Purpose: Show the user their scroll distance today — clearly, instantly, without asking them to think.
 * User Question: "How much have I scrolled today?"
 *
 * Visual hierarchy (top → bottom):
 *   1. Large Title App Bar: "Scrolla" + Settings gear
 *   2. Hero metric: displayLarge number in Primary coral
 *   3. Unit label: "km"
 *   4. Landmark comparison: relatable real-world distance
 *   5. Rank chip: tappable, links to Leaderboard
 *   6. Rotating insight card
 *
 * Coral budget: 1 moment (hero number only)
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    // Mock data — these will be replaced by ViewModel
    scrollDistanceKm: Float = 2.3f,
    landmarkText: String = "about the height of 3 Burj Khalifas",
    rankPosition: Int = 2,
    groupSize: Int = 5,
    groupName: String = "College Friends",
    insightLabel: String = "peak scroll time",
    insightBody: String = "Most of your scrolling happens 10pm–11pm — that's your commute distance, but at 10pm",
    onSettingsClick: () -> Unit = {},
    onRankChipClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val scrollState = rememberScrollState()

    var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isVisible = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ambientGlow")
    val glowCenterY by infiniteTransition.animateFloat(
        initialValue = -0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowCenterY"
    )
    val glowCenterX by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowCenterX"
    )
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Root Ambient Glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.06f),
                                primaryColor.copy(alpha = 0.0f)
                            ),
                            center = Offset(size.width * glowCenterX, size.height * glowCenterY),
                            radius = size.width * 1.5f
                        )
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(scrollState)
        ) {
            // ─── TOP APP BAR (Large Title) ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = spacing.medium,
                        end = spacing.extraSmall,
                        top = spacing.medium,
                        bottom = spacing.extraSmall
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = ScrollaStrings.HOME_TITLE,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ─── HERO METRIC SECTION ───────────────────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 100)) + androidx.compose.animation.slideInVertically(tween(400, delayMillis = 100), initialOffsetY = { 30 })
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium)
                        .padding(top = spacing.large),
                    horizontalAlignment = Alignment.Start
                ) {
                    val formattedDistance = ScrollaFormatters.formatDistance(scrollDistanceKm)
                    
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "$formattedDistance kilometres"
                        }
                    ) {
                        Text(
                            text = formattedDistance,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 120.sp,
                                lineHeight = 120.sp,
                                letterSpacing = (-0.05).em,
                                fontWeight = FontWeight.Light,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.alignByBaseline()
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = ScrollaStrings.HOME_UNIT,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Normal,
                                letterSpacing = (-0.02).em
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline()
                        )
                    }

                    Spacer(modifier = Modifier.height(spacing.small))

                    Text(
                        text = landmarkText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-0.01).em
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // ─── RANK ROW ─────────────────────────────────────────────
            Spacer(modifier = Modifier.height(spacing.extraLarge))

            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 200)) + androidx.compose.animation.slideInVertically(tween(400, delayMillis = 200), initialOffsetY = { 30 })
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium)
                        .bentoCard(
                            padding = spacing.medium,
                            onClick = onRankChipClick
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Column {
                            Text(
                                text = "Leaderboard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${ScrollaFormatters.formatOrdinal(rankPosition)} of $groupSize in $groupName",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ─── INSIGHT CARD ──────────────────────────────────────────
            Spacer(modifier = Modifier.height(spacing.medium))

            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 300)) + androidx.compose.animation.slideInVertically(tween(400, delayMillis = 300), initialOffsetY = { 30 })
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium)
                        .bentoCard()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = insightLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.05.em,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = insightBody,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 24.sp,
                            letterSpacing = (-0.01).em
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Bottom clearance above nav bar — xxl (48dp)
            Spacer(modifier = Modifier.height(spacing.extraExtraLarge))
        }
    }
}


@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeScreenPreview() {
    ScrollaUILabTheme {
        HomeScreen()
    }
}

