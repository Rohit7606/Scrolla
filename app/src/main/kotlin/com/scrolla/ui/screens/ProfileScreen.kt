package com.scrolla.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.spacing
import com.scrolla.ui.components.bentoCard
import com.scrolla.ui.components.bounceClick

/**
 * Screen 8 â€” Profile Tab
 *
 * Identity/achievement hub in the Strava/Duolingo mold.
 * Teaser hub: every card reuses copy and data already defined
 * for Personal Records (Screen 13) and Hall of Fame (Screen 14).
 *
 * Visual hierarchy:
 *   1. Identity header (display name + avatar initial + settings gear)
 *   2. Personal best teaser card
 *   3. Hall of fame status teaser card
 *   4. Groups teaser card
 */
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    // Mock data â€” will come from ViewModel
    displayName: String = "Rohit",
    personalBestKm: Float = 0.4f,
    personalBestRelativeDate: String = "3 weeks ago",
    hallOfFameGapKm: Float = 1.9f,
    isRecordHolder: Boolean = false,
    groupCount: Int = 2,
    primaryGroupName: String = "College Friends",
    onSettingsClick: () -> Unit = {},
    onPersonalRecordsClick: () -> Unit = {},
    onHallOfFameClick: () -> Unit = {},
    onManageGroupsClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val scrollState = rememberScrollState()

    var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isVisible = true
    }

    val glowCenterY = 0.1f
    val primaryColor = MaterialTheme.colorScheme.primary

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
                                primaryColor.copy(alpha = 0.05f),
                                primaryColor.copy(alpha = 0.0f)
                            ),
                            center = Offset(size.width * 0.3f, size.height * glowCenterY),
                            radius = size.width * 1.2f
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
            // â”€â”€â”€ IDENTITY HEADER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(tween(400)) + androidx.compose.animation.slideInVertically(tween(400), initialOffsetY = { 20 })
            ) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar circle with initial
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() }
                        )
                    }

                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.extraLarge))

            // â”€â”€â”€ PERSONAL BEST TEASER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 100)) + androidx.compose.animation.slideInVertically(tween(400, delayMillis = 100), initialOffsetY = { 20 })
            ) {
                ProfileTeaser(
                    icon = Icons.Outlined.WorkspacePremium,
                    label = ScrollaStrings.PROFILE_PERSONAL_BEST_LABEL,
                    tapLabel = ScrollaStrings.PROFILE_PERSONAL_BEST_LINK,
                    onClick = onPersonalRecordsClick,
                    modifier = Modifier.padding(horizontal = spacing.medium)
                ) {
                    if (personalBestKm > 0f) {
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = ScrollaFormatters.formatDistance(personalBestKm),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = (-0.03).em,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.alignByBaseline()
                            )
                            Text(
                                text = " km",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline()
                            )
                            Text(
                                text = " Â· $personalBestRelativeDate",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                    } else {
                        Text(
                            text = ScrollaStrings.PROFILE_PERSONAL_BEST_EMPTY,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // â”€â”€â”€ HALL OF FAME STATUS TEASER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 200)) + androidx.compose.animation.slideInVertically(tween(400, delayMillis = 200), initialOffsetY = { 20 })
            ) {
                ProfileTeaser(
                    icon = Icons.Outlined.EmojiEvents,
                    label = "hall of fame",
                    tapLabel = ScrollaStrings.PROFILE_HALL_OF_FAME_LINK,
                    onClick = onHallOfFameClick,
                    modifier = Modifier.padding(horizontal = spacing.medium)
                ) {
                    Text(
                        text = when {
                            isRecordHolder -> ScrollaStrings.PROFILE_HALL_OF_FAME_RECORD_HOLDER
                            hallOfFameGapKm > 0f -> String.format(
                                ScrollaStrings.HALL_OF_FAME_PROGRESS_TEMPLATE,
                                "${ScrollaFormatters.formatDistance(hallOfFameGapKm)} km"
                            )
                            else -> ScrollaStrings.PROFILE_HALL_OF_FAME_EMPTY
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 24.sp,
                            letterSpacing = (-0.01).em
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // â”€â”€â”€ GROUPS TEASER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 300)) + androidx.compose.animation.slideInVertically(tween(400, delayMillis = 300), initialOffsetY = { 20 })
            ) {
                ProfileTeaser(
                    icon = Icons.Outlined.Groups,
                    label = "groups",
                    tapLabel = ScrollaStrings.PROFILE_GROUPS_LINK,
                    onClick = onManageGroupsClick,
                    modifier = Modifier.padding(horizontal = spacing.medium)
                ) {
                    Text(
                        text = "$groupCount groups Â· $primaryGroupName on widget",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 24.sp,
                            letterSpacing = (-0.01).em
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.extraExtraLarge))
        }
    }
}

/**
 * Reusable teaser card for the Profile tab.
 * Matches the HomeScreen insight card visual language:
 * - `surfaceVariant.copy(alpha = 0.15f)` background
 * - `RoundedCornerShape(24.dp)`
 * - Editorial uppercase label
 * - Tap affordance with arrow
 */
@Composable
private fun ProfileTeaser(
    icon: ImageVector,
    label: String,
    tapLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .bentoCard()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.05.em,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        content()

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = tapLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfileScreenPreview() {
    ScrollaUILabTheme {
        ProfileScreen()
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenDarkPreview() {
    ScrollaUILabTheme(darkTheme = true) {
        ProfileScreen()
    }
}
