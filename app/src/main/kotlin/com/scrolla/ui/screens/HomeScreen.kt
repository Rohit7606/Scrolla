package com.scrolla.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrolla.ui.components.DeltaChip
import com.scrolla.ui.components.ScrollaCard
import com.scrolla.ui.components.SectionLabel
import com.scrolla.ui.theme.PillShape
import com.scrolla.ui.theme.ScrollaType
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.scrollaColors
import com.scrolla.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home — "how much have I scrolled today?"
 *
 * The accent appears exactly once on this screen: the figure. It is not
 * on the section labels, the card icons, the rank strip or the glow —
 * there is no glow. Coral means you, here, now.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    // Mock data — these will be replaced by ViewModel
    scrollDistanceKm: Float = 2.3f,
    yesterdayKm: Float? = 2.8f,
    landmarkText: String = "about the height of three Burj Khalifas",
    rankPosition: Int = 2,
    groupSize: Int = 5,
    groupName: String = "College Friends",
    insightLabel: String = "peak scroll time",
    insightBody: String = "Most of it happens between 10 and 11pm. A commute's worth of distance, at bedtime.",
    onSettingsClick: () -> Unit = {},
    onRankChipClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.scrollaColors
    val scrollState = rememberScrollState()

    // Animates when the number CHANGES, not when the screen arrives. On
    // first composition it snaps, so nothing is withheld from the user
    // and switching tabs never replays an entrance.
    val displayedDistance by animateFloatAsState(
        targetValue = scrollDistanceKm,
        animationSpec = tween(700),
        label = "distanceCount"
    )

    val dateLabel = remember {
        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date()).uppercase()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(scrollState)
    ) {

        // ─── HEADER ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.screenInset, end = spacing.extraSmall, top = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = ScrollaStrings.APP_NAME.uppercase(),
                style = ScrollaType.Micro,
                color = colors.textLow
            )
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(20.dp),
                    tint = colors.textLow
                )
            }
        }

        // ─── HERO ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = 38.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionLabel("TODAY · $dateLabel")

            val formatted = ScrollaFormatters.formatDistance(displayedDistance)
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "$formatted kilometres today"
                }
            ) {
                Text(
                    text = formatted,
                    style = ScrollaType.Figure,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = ScrollaStrings.HOME_UNIT,
                    style = ScrollaType.Row.copy(fontSize = ScrollaType.Row.fontSize * 1.2f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )
            }

            Text(
                text = landmarkText,
                style = ScrollaType.Editorial,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (yesterdayKm != null) {
                val delta = scrollDistanceKm - yesterdayKm
                val improving = delta <= 0f
                val magnitude = ScrollaFormatters.formatDistance(kotlin.math.abs(delta))
                DeltaChip(
                    text = if (improving) {
                        "$magnitude km less than yesterday"
                    } else {
                        "$magnitude km more than yesterday"
                    },
                    improving = improving
                )
            }
        }

        // ─── CARDS ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = spacing.sectionGap),
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap)
        ) {

            ScrollaCard(onClick = onRankChipClick) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SectionLabel("STANDING")
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = ScrollaFormatters.formatOrdinal(rankPosition),
                                style = ScrollaType.FigureMedium.copy(fontSize = 30.sp, lineHeight = 30.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.alignByBaseline()
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "of $groupSize",
                                style = ScrollaType.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                        Text(
                            text = groupName,
                            style = ScrollaType.Caption,
                            color = colors.textLow
                        )
                    }

                    RankStrip(rankPosition = rankPosition, groupSize = groupSize)
                }
            }

            ScrollaCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colors.textLow
                        )
                        SectionLabel(insightLabel)
                    }
                    Text(
                        text = insightBody,
                        style = ScrollaType.Body,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.extraExtraLarge))
    }
}

/**
 * Where you sit in the group, as shape rather than colour — so the card
 * can carry a second piece of information without spending the accent.
 */
@Composable
private fun RankStrip(
    rankPosition: Int,
    groupSize: Int,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.scrollaColors
    val bars = groupSize.coerceIn(1, 8)

    Row(
        modifier = modifier.semantics {
            contentDescription = "Rank $rankPosition of $groupSize"
        },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        for (i in 1..bars) {
            val isSelf = i == rankPosition
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .height((16 + (i - 1) * 7).dp)
                    .clip(PillShape)
                    .background(if (isSelf) MaterialTheme.colorScheme.onSurface else colors.textFaint)
            )
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

@Preview(showBackground = true, widthDp = 393, heightDp = 852, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    ScrollaUILabTheme(darkTheme = true) {
        HomeScreen()
    }
}
