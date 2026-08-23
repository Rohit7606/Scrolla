package com.scrolla.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scrolla.model.DistanceFormatter
import com.scrolla.ui.components.ScrollaCard
import com.scrolla.ui.components.SectionLabel
import com.scrolla.ui.theme.PillShape
import com.scrolla.ui.theme.ScrollaType
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.scrollaColors
import com.scrolla.ui.theme.spacing

data class DayData(
    val dayLabel: String,
    val distanceKm: Float,
    val isToday: Boolean = false,
    val isFuture: Boolean = false,
    val fullLabel: String = dayLabel
)

data class AppUsage(
    val appName: String,
    val distanceKm: Float
)

/**
 * Insights — "what patterns exist in my scrolling?"
 *
 * The chart is the fix for the worst interaction bug in the app: the bars
 * were drawn into a Canvas with no hit testing, so the only tappable
 * thing was the single-letter label underneath it, and the selection had
 * no visual state at all. Each bar is now its own full-height column,
 * comfortably over the 44dp target, and the selection is visible.
 */
@Composable
fun InsightsScreen(
    modifier: Modifier = Modifier,
    weekData: List<DayData> = listOf(
        DayData("M", 1.8f, fullLabel = "Monday"),
        DayData("T", 2.5f, fullLabel = "Tuesday"),
        DayData("W", 1.2f, fullLabel = "Wednesday"),
        DayData("T", 2.3f, isToday = true, fullLabel = "Today"),
        DayData("F", 0f, isFuture = true, fullLabel = "Friday"),
        DayData("S", 0f, isFuture = true, fullLabel = "Saturday"),
        DayData("S", 0f, isFuture = true, fullLabel = "Sunday")
    ),
    topApps: List<AppUsage> = listOf(
        AppUsage("Instagram", 1.0f),
        AppUsage("Reddit", 0.6f),
        AppUsage("Twitter", 0.3f),
        AppUsage("YouTube", 0.2f),
        AppUsage("Chrome", 0.1f)
    ),
    peakTimeText: String? = "Most of it happens between 10 and 11pm. A commute's worth of distance, at bedtime.",
    onAppBreakdownClick: () -> Unit = {},
    onShowRecapClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.scrollaColors
    val scrollState = rememberScrollState()

    var selectedDay by remember {
        mutableIntStateOf(weekData.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: 0)
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
            SectionLabel("PRIVATE TO THIS PHONE")
            Text(
                text = ScrollaStrings.INSIGHTS_TITLE,
                style = ScrollaType.Display,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // ─── WEEK CHART ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = spacing.sectionGap),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionLabel(ScrollaStrings.INSIGHTS_SECTION_THIS_WEEK)

            val maxKm = weekData.maxOfOrNull { it.distanceKm }?.takeIf { it > 0f } ?: 1f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .semantics { contentDescription = "Weekly scroll distance. Tap a day to see its total." },
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                weekData.forEachIndexed { index, day ->
                    DayBar(
                        day = day,
                        maxKm = maxKm,
                        selected = index == selectedDay,
                        onClick = { selectedDay = index },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val current = weekData.getOrNull(selectedDay)
            if (current != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = current.fullLabel,
                        style = ScrollaType.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline()
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    if (current.distanceKm > 0f) {
                        Text(
                            text = DistanceFormatter.formatDisplayValue(current.distanceKm),
                            style = ScrollaType.FigureMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.alignByBaseline()
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = DistanceFormatter.formatDisplayUnit(current.distanceKm),
                            style = ScrollaType.Caption,
                            color = colors.textLow,
                            modifier = Modifier.alignByBaseline()
                        )
                    } else {
                        Text(
                            text = ScrollaStrings.INSIGHTS_NO_DATA_DAY,
                            style = ScrollaType.Body,
                            color = colors.textLow,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }
            }
        }

        // ─── TOP APPS ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = spacing.sectionGap),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAppBreakdownClick)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionLabel(ScrollaStrings.INSIGHTS_SECTION_TOP_APPS)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "All apps",
                        style = ScrollaType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.textLow
                    )
                }
            }

            val maxDistance = topApps.maxOfOrNull { it.distanceKm }?.takeIf { it > 0f } ?: 1f
            val totalDistance = topApps.sumOf { it.distanceKm.toDouble() }.toFloat()

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                topApps.take(5).forEachIndexed { index, app ->
                    AppRow(
                        appName = app.appName,
                        distanceKm = app.distanceKm,
                        progress = app.distanceKm / maxDistance,
                        percentage = if (totalDistance > 0f) {
                            ((app.distanceKm / totalDistance) * 100).toInt()
                        } else 0,
                        isTopApp = index == 0
                    )
                }
            }
        }

        // ─── PEAK TIME ─────────────────────────────────────────────
        // Hidden until a peak hour actually exists — there is no honest peak
        // to name before the first day of data lands.
        if (peakTimeText != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenInset)
                    .padding(top = spacing.sectionGap),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionLabel(ScrollaStrings.INSIGHTS_SECTION_PEAK_TIME)
                Text(
                    text = peakTimeText,
                    style = ScrollaType.Body,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ─── WEEKLY RECAP ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = spacing.sectionGap)
        ) {
            ScrollaCard(onClick = onShowRecapClick) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("WEEKLY RECAP")
                        Text(
                            text = ScrollaStrings.RECAP_HEADLINE,
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

        // ─── PRIVACY NOTE ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = spacing.large),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(13.dp),
                tint = colors.textLow
            )
            Text(
                text = ScrollaStrings.INSIGHTS_PRIVACY_NOTE,
                style = ScrollaType.Caption,
                color = colors.textLow,
                modifier = Modifier.semantics {
                    contentDescription = "Privacy note: ${ScrollaStrings.INSIGHTS_PRIVACY_NOTE}"
                }
            )
        }

        Spacer(modifier = Modifier.height(spacing.extraExtraLarge))
    }
}

/**
 * One bar plus its label, as a single tappable column. Today stays the
 * accent whichever day is selected, so you never lose your place.
 */
@Composable
private fun DayBar(
    day: DayData,
    maxKm: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.scrollaColors

    val targetHeight = if (day.distanceKm > 0f) {
        (120f * (day.distanceKm / maxKm)).dp.coerceAtLeast(6.dp)
    } else {
        6.dp
    }
    val barHeight by animateDpAsState(targetHeight, tween(450), label = "barHeight")

    val fill = when {
        day.isToday -> MaterialTheme.colorScheme.primary
        day.isFuture || day.distanceKm == 0f -> MaterialTheme.colorScheme.surfaceVariant
        selected -> MaterialTheme.colorScheme.onSurface
        else -> colors.textFaint
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (day.distanceKm > 0f) {
                    "${day.fullLabel}, ${DistanceFormatter.formatDistanceSpoken(day.distanceKm)}"
                } else {
                    "${day.fullLabel}, no data yet"
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                .background(fill)
        )
        Text(
            text = day.dayLabel,
            style = ScrollaType.Caption.copy(
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Medium
                else androidx.compose.ui.text.font.FontWeight.Normal
            ),
            color = if (selected) MaterialTheme.colorScheme.onSurface else colors.textLow,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun AppRow(
    appName: String,
    distanceKm: Float,
    progress: Float,
    percentage: Int,
    isTopApp: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.scrollaColors
    val barColor = if (isTopApp) colors.textLow else colors.textFaint

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "$appName, $percentage percent of today's total, ${DistanceFormatter.formatDistanceSpoken(distanceKm)}"
            },
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = appName,
                style = ScrollaType.Body,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = DistanceFormatter.formatDistance(distanceKm),
                style = ScrollaType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(PillShape)
                    .background(barColor)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun InsightsScreenPreview() {
    ScrollaUILabTheme {
        InsightsScreen()
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InsightsScreenDarkPreview() {
    ScrollaUILabTheme(darkTheme = true) {
        InsightsScreen()
    }
}
