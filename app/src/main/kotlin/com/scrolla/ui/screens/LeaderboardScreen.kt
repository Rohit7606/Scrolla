package com.scrolla.ui.screens

import com.scrolla.ui.components.bentoCard
import com.scrolla.ui.components.bounceClick
import androidx.compose.material.icons.automirrored.outlined.ArrowForward

import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.spacing

/**
 * Screen 6 — Leaderboard
 *
 * Purpose: Show the user where they stand among friends today — with context, without shame.
 * User Question: "How does my scrolling compare to my group?"
 *
 * Coral budget: 1 thread (Most Improved banner → self-row → Hall of Fame link)
 */

data class LeaderboardEntry(
    val displayName: String,
    val distanceKm: Float,
    val isSelf: Boolean = false
)

data class GroupStats(
    val todayKm: Float = 3.4f,
    val yesterdayKm: Float = 2.8f,
    val weekAvgKm: Float = 3.1f
)

@Composable
fun LeaderboardScreen(
    modifier: Modifier = Modifier,
    // Mock data — will be replaced by ViewModel
    mostImprovedName: String? = "Priya",
    entries: List<LeaderboardEntry> = listOf(
        LeaderboardEntry("Priya", 1.2f),
        LeaderboardEntry("You", 2.3f, isSelf = true),
        LeaderboardEntry("Jordan", 3.1f),
        LeaderboardEntry("Alex", 4.5f),
        LeaderboardEntry("Sam", 6.8f)
    ),
    groupStats: GroupStats = GroupStats(),
    groupBestDay: String = "0.4 km by Priya",
    onHallOfFameClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val scrollState = rememberScrollState()

    var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isVisible = true
    }

    val glowCenterY = 0.2f
    val glowCenterX = 0.8f
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.05f),
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
        // â”€â”€â”€ TOP APP BAR (Large Title) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        androidx.compose.animation.AnimatedVisibility(
            visible = isVisible,
            enter = androidx.compose.animation.fadeIn(tween(400)) + androidx.compose.animation.slideInVertically(tween(400), initialOffsetY = { 20 })
        ) {
            Text(
                text = ScrollaStrings.LEADERBOARD_TITLE,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(
                        start = spacing.medium,
                        end = spacing.medium,
                        top = spacing.medium,
                        bottom = spacing.extraSmall
                    )
            )
        }

        // â”€â”€â”€ MOST IMPROVED BANNER (Conditional) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Per blueprint: only show if â‰¥ 10% improvement this week
        // Uses PrimaryContainer — THE coral moment for this screen
        if (mostImprovedName != null) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 100)) + androidx.compose.animation.slideInVertically(tween(400, delayMillis = 100), initialOffsetY = { 20 })
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium)
                        .padding(top = spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$mostImprovedName ${ScrollaStrings.LEADERBOARD_MOST_IMPROVED}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // â”€â”€â”€ GROUP STATS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Spacer(modifier = Modifier.height(spacing.medium))

        androidx.compose.animation.AnimatedVisibility(
            visible = isVisible,
            enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 200)) + androidx.compose.animation.slideInVertically(tween(400, delayMillis = 200), initialOffsetY = { 20 })
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium, vertical = spacing.large),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = ScrollaStrings.LEADERBOARD_STAT_TODAY.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.05.em,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "%.1f".format(groupStats.todayKm),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-0.04).em,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.alignByBaseline()
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "km",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatColumn(label = ScrollaStrings.LEADERBOARD_STAT_YESTERDAY, value = "%.1f".format(groupStats.yesterdayKm))
                    StatColumn(label = ScrollaStrings.LEADERBOARD_STAT_WEEK_AVG, value = "%.1f".format(groupStats.weekAvgKm))
                }
            }
        }

        // â”€â”€â”€ LEADERBOARD LIST â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Per blueprint: ListItem rows, self-row highlighted with PrimaryContainer wash
        Spacer(modifier = Modifier.height(spacing.extraLarge))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            entries.forEachIndexed { index, entry ->
                val rank = index + 1
                
                androidx.compose.animation.AnimatedVisibility(
                    visible = isVisible,
                    enter = androidx.compose.animation.fadeIn(tween(300, delayMillis = 300 + (index * 50))) + 
                            androidx.compose.animation.slideInVertically(tween(300, delayMillis = 300 + (index * 50)), initialOffsetY = { 20 })
                ) {
                    LeaderboardRow(
                        rank = rank,
                        name = if (entry.isSelf) ScrollaStrings.LEADERBOARD_SELF_NAME else entry.displayName,
                        distanceKm = entry.distanceKm,
                        isSelf = entry.isSelf
                    )
                }
                
                if (index < entries.lastIndex) {
                    Spacer(modifier = Modifier.height(spacing.extraExtraSmall))
                }
            }
        }

        // â”€â”€â”€ HALL OF FAME TEASER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Per blueprint: Card (Interactive), "Hall of fame →" in Primary coral
        Spacer(modifier = Modifier.height(spacing.extraLarge))

        androidx.compose.animation.AnimatedVisibility(
            visible = isVisible,
            enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 600)) + 
                    androidx.compose.animation.slideInVertically(tween(400, delayMillis = 600), initialOffsetY = { 20 })
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium)
                    .bentoCard()
                    .bounceClick { onHallOfFameClick() }
                    .padding(spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${ScrollaStrings.LEADERBOARD_HALL_OF_FAME_PREFIX} $groupBestDay",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Text(
                        text = ScrollaStrings.LEADERBOARD_HALL_OF_FAME_LINK,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom clearance
        Spacer(modifier = Modifier.height(spacing.extraExtraLarge))
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 0.05.em,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Individual leaderboard row.
 *
 * Per blueprint:
 * - Rank: titleLarge, ordinal suffix full-size (no superscript)
 * - Name: bodyLarge
 * - Distance: bodyLarge, right-aligned, tabular figures
 * - Self row: PrimaryContainer wash background
 * - Min height: 56dp (with 48dp touch target)
 */
@Composable
private fun LeaderboardRow(
    rank: Int,
    name: String,
    distanceKm: Float,
    isSelf: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val backgroundColor = if (isSelf) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    
    val textColor = if (isSelf) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    val semanticLabel = "${ScrollaFormatters.formatOrdinal(rank)}, $name, ${"%.1f".format(distanceKm)} kilometres" +
        if (isSelf) ", highlighted" else ""

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = spacing.medium, vertical = spacing.small)
            .semantics { contentDescription = semanticLabel },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank ordinal — massive, thin, transparent
        Text(
            text = "$rank",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Thin,
                fontFeatureSettings = "tnum",
                letterSpacing = (-0.05).em
            ),
            color = textColor.copy(alpha = 0.4f),
            modifier = Modifier.width(56.dp)
        )

        // Name
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Normal
            ),
            color = textColor,
            modifier = Modifier.weight(1f)
        )

        // Distance — right-aligned, tabular figures
        Text(
            text = "%.1f".format(distanceKm),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
                letterSpacing = (-0.02).em
            ),
            color = textColor,
            textAlign = TextAlign.End
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "km",
            style = MaterialTheme.typography.bodyLarge,
            color = textColor.copy(alpha = 0.7f),
            textAlign = TextAlign.End
        )
    }
}


@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LeaderboardScreenPreview() {
    ScrollaUILabTheme {
        LeaderboardScreen()
    }
}
