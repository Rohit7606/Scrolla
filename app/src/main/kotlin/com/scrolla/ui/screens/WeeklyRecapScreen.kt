package com.scrolla.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scrolla.ui.components.ScrollaPrimaryButton
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.spacing

/**
 * Screen 12 â€” Weekly Recap (Interstitial / Spotify Wrapped style)
 * 
 * Pops up once a week. Extremely high visual impact.
 * Heavily relies on the ambient glow and large typography.
 */
@Composable
fun WeeklyRecapScreen(
    modifier: Modifier = Modifier,
    weeklyDistanceKm: Float = 14.2f,
    landmarkText: String = "that's longer than a half marathon",
    onShareClick: () -> Unit = {},
    onSkipClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing

    // Dramatic Ambient glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "recapGlow")
    val glowCenterY by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowY"
    )
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadius"
    )
    val primaryColor = MaterialTheme.colorScheme.primary

    var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isVisible = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.15f), // Stronger glow for recap
                                primaryColor.copy(alpha = 0.0f)
                            ),
                            center = Offset(size.width * 0.5f, size.height * glowCenterY),
                            radius = size.width * glowRadius
                        )
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.15f))

            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500)) + 
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(500), initialOffsetY = { 20 })
            ) {
                Text(
                    text = ScrollaStrings.RECAP_HEADLINE.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 0.1.em,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(spacing.extraLarge))

            // Massive Metric
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500, delayMillis = 200)) + 
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(500, delayMillis = 200), initialOffsetY = { 40 })
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = ScrollaFormatters.formatDistance(weeklyDistanceKm),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 140.sp,
                            lineHeight = 140.sp,
                            letterSpacing = (-0.05).em,
                            fontWeight = FontWeight.Light,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "km",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-0.02).em
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))
            
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500, delayMillis = 400)) + 
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(500, delayMillis = 400), initialOffsetY = { 20 })
            ) {
                Text(
                    text = ScrollaStrings.RECAP_STAT_SUFFIX,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(0.15f))

            // Landmark
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500, delayMillis = 700)) + 
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(500, delayMillis = 700), initialOffsetY = { 20 })
            ) {
                Text(
                    text = landmarkText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.01).em,
                        lineHeight = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = spacing.large)
                )
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // Footer branding
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500, delayMillis = 1000))
            ) {
                Text(
                    text = ScrollaStrings.RECAP_FOOTER,
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.2.em,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(spacing.large))

            // Actions
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500, delayMillis = 1200)) + 
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(500, delayMillis = 1200), initialOffsetY = { 20 })
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ScrollaPrimaryButton(
                        text = ScrollaStrings.RECAP_SHARE,
                        onClick = onShareClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(spacing.small))
                    
                    TextButton(
                        onClick = onSkipClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = ScrollaStrings.RECAP_SKIP,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(spacing.medium))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WeeklyRecapScreenPreview() {
    ScrollaUILabTheme {
        WeeklyRecapScreen()
    }
}

