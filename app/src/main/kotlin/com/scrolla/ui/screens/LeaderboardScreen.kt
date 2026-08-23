package com.scrolla.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scrolla.model.DistanceFormatter
import com.scrolla.ui.components.ScrollaCard
import com.scrolla.ui.components.SectionLabel
import com.scrolla.ui.theme.RowShape
import com.scrolla.ui.theme.ScrollaType
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.scrollaColors
import com.scrolla.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LeaderboardEntry(
    val displayName: String,
    val distanceKm: Float,
    val isSelf: Boolean = false
)

data class GroupStats(
    val todayKm: Float = 2.3f,
    val yesterdayKm: Float = 2.8f,
    val weekAvgKm: Float = 3.1f
)

/**
 * Leaderboard — "where do I stand today?"
 *
 * The accent appears once: your row. The most-improved line uses the
 * improving colour, because it is a direction, not a brand moment.
 */
@Composable
fun LeaderboardScreen(
    modifier: Modifier = Modifier,
    groupName: String = "College Friends",
    mostImprovedName: String? = "Lewis",
    entries: List<LeaderboardEntry> = listOf(
        LeaderboardEntry("Lewis", 1.2f),
        LeaderboardEntry("You", 2.3f, isSelf = true),
        LeaderboardEntry("Max", 3.1f),
        LeaderboardEntry("Alex", 4.5f),
        LeaderboardEntry("Stroll", 6.8f)
    ),
    groupStats: GroupStats = GroupStats(),
    groupBestDay: String? = "0.4 km by Lewis",
    /** Shown in place of the rows — distinguishes "no group" from "no totals synced yet". */
    emptyBoardMessage: String = ScrollaStrings.LEADERBOARD_EMPTY_NO_TOTALS,
    onHallOfFameClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.scrollaColors
    val scrollState = rememberScrollState()

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionLabel("GROUP · $dateLabel")
            Text(
                text = groupName,
                style = ScrollaType.Display,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // ─── GROUP STAT STRIP ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = spacing.large)
        ) {
            ScrollaCard(padding = spacing.medium) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatColumn(
                        label = ScrollaStrings.LEADERBOARD_STAT_TODAY,
                        value = DistanceFormatter.formatKmValue(groupStats.todayKm),
                        emphasised = true,
                        modifier = Modifier.weight(1f)
                    )
                    StatDivider()
                    StatColumn(
                        label = ScrollaStrings.LEADERBOARD_STAT_YESTERDAY,
                        value = DistanceFormatter.formatKmValue(groupStats.yesterdayKm),
                        modifier = Modifier.weight(1f)
                    )
                    StatDivider()
                    StatColumn(
                        label = ScrollaStrings.LEADERBOARD_STAT_WEEK_AVG,
                        value = DistanceFormatter.formatKmValue(groupStats.weekAvgKm),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ─── THE BOARD ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = spacing.large),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = emptyBoardMessage,
                    style = ScrollaType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    LeaderboardRow(
                        rank = index + 1,
                        name = if (entry.isSelf) ScrollaStrings.LEADERBOARD_SELF_NAME else entry.displayName,
                        distanceKm = entry.distanceKm,
                        isSelf = entry.isSelf
                    )
                }
            }
        }

        // ─── MOST IMPROVED ─────────────────────────────────────────
        if (mostImprovedName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenInset + 14.dp)
                    .padding(top = spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.TrendingDown,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = colors.improving
                )
                Text(
                    text = "$mostImprovedName${ScrollaStrings.LEADERBOARD_MOST_IMPROVED}",
                    style = ScrollaType.Caption,
                    color = colors.improving
                )
            }
        }

        // ─── HALL OF FAME ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = spacing.large)
        ) {
            ScrollaCard(onClick = onHallOfFameClick) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel(ScrollaStrings.LEADERBOARD_HALL_OF_FAME_LINK)
                        Text(
                            text = groupBestDay
                                ?.let { "${ScrollaStrings.LEADERBOARD_HALL_OF_FAME_PREFIX} $it" }
                                ?: ScrollaStrings.LEADERBOARD_NO_RECORD_YET,
                            style = ScrollaType.Body,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.textLow
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.extraExtraLarge))
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.scrollaColors.cardBorder)
    )
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SectionLabel(label)
        Text(
            text = value,
            style = ScrollaType.FigureSmall,
            color = if (emphasised) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * One row. Rank is a small readable numeral rather than a 44sp Thin
 * figure at 40% alpha — the old treatment was decorative to the point of
 * being unreadable, which defeats the only thing a rank column is for.
 */
@Composable
private fun LeaderboardRow(
    rank: Int,
    name: String,
    distanceKm: Float,
    isSelf: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.scrollaColors
    val accent = MaterialTheme.colorScheme.primary

    val semanticLabel = "${ScrollaFormatters.formatOrdinal(rank)}, $name, " +
        "${DistanceFormatter.formatKmValue(distanceKm)} kilometres" + if (isSelf) ", you" else ""

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(if (isSelf) colors.selfRow else Color.Transparent)
            .then(
                if (isSelf) Modifier.border(1.dp, accent.copy(alpha = 0.28f), RowShape) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .semantics { contentDescription = semanticLabel },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = ScrollaType.Caption.copy(
                fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Medium,
                fontFeatureSettings = "tnum"
            ),
            color = if (isSelf) accent else colors.textLow,
            modifier = Modifier.width(18.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = name,
            style = ScrollaType.Row.copy(
                fontWeight = if (isSelf) FontWeight.Medium else FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = DistanceFormatter.formatKmValue(distanceKm),
                style = ScrollaType.FigureSmall,
                color = if (isSelf) accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline()
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "km",
                style = ScrollaType.Caption,
                color = colors.textLow,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LeaderboardScreenPreview() {
    ScrollaUILabTheme {
        LeaderboardScreen()
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LeaderboardScreenDarkPreview() {
    ScrollaUILabTheme(darkTheme = true) {
        LeaderboardScreen()
    }
}
