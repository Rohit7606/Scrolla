package com.scrolla.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.spacing
import com.scrolla.ui.components.bentoCard

/**
 * Screen 7 — Insights
 *
 * Purpose: Give the user a deeper, private look at their scrolling patterns.
 * User Question: "What patterns exist in my scrolling?"
 *
 * Coral budget: 1 thread (today's bar → #1 app progress fill)
 */

data class DayData(
    val dayLabel: String,
    val distanceKm: Float,
    val isToday: Boolean = false,
    val isFuture: Boolean = false
)

data class AppUsage(
    val appName: String,
    val distanceKm: Float
)

@Composable
fun InsightsScreen(
    modifier: Modifier = Modifier,
    // Mock data — will be replaced by ViewModel
    weekData: List<DayData> = listOf(
        DayData("M", 1.8f),
        DayData("T", 2.5f),
        DayData("W", 1.2f),
        DayData("T", 3.1f, isToday = true),
        DayData("F", 0f, isFuture = true),
        DayData("S", 0f, isFuture = true),
        DayData("S", 0f, isFuture = true)
    ),
    topApps: List<AppUsage> = listOf(
        AppUsage("Instagram", 1.2f),
        AppUsage("Reddit", 0.8f),
        AppUsage("Twitter", 0.3f),
        AppUsage("YouTube", 0.2f),
        AppUsage("Chrome", 0.1f)
    ),
    peakTimeText: String = "Most of your scrolling happens 10pmâ€“11pm — that's your commute distance, but at 10pm",
    onAppBreakdownClick: () -> Unit = {},
    onShowRecapClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val scrollState = rememberScrollState()

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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ScrollaStrings.INSIGHTS_TITLE,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            androidx.compose.material3.TextButton(onClick = onShowRecapClick) {
                Text(text = "Weekly Recap")
            }
        }

        // â”€â”€â”€ SECTION: WEEKLY CHART â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Spacer(modifier = Modifier.height(spacing.large))

        Text(
            text = ScrollaStrings.INSIGHTS_SECTION_THIS_WEEK.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 0.05.em,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = spacing.medium)
        )

        Spacer(modifier = Modifier.height(spacing.small))

        WeeklyBarChart(
            data = weekData,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium)
        )

        // â”€â”€â”€ SECTION: TOP APPS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Spacer(modifier = Modifier.height(spacing.extraLarge))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ScrollaStrings.INSIGHTS_SECTION_TOP_APPS.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.05.em,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
            androidx.compose.material3.TextButton(onClick = onAppBreakdownClick) {
                Text(text = "Full Breakdown")
            }
        }

        Spacer(modifier = Modifier.height(spacing.small))

        TopAppsList(
            apps = topApps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium)
        )

        // â”€â”€â”€ SECTION: PEAK SCROLL TIME â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Spacer(modifier = Modifier.height(spacing.extraLarge))

        Text(
            text = ScrollaStrings.INSIGHTS_SECTION_PEAK_TIME.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 0.05.em,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = spacing.medium)
        )

        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            text = peakTimeText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Light,
                lineHeight = 28.sp,
                letterSpacing = (-0.01).em
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = spacing.medium)
        )

        // â”€â”€â”€ PRIVACY NOTE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Per blueprint: MUST always be visible — trust signal
        Spacer(modifier = Modifier.height(spacing.medium))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = ScrollaStrings.INSIGHTS_PRIVACY_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics {
                    contentDescription = "Privacy note: ${ScrollaStrings.INSIGHTS_PRIVACY_NOTE}"
                }
            )
        }

        // Bottom clearance
        Spacer(modifier = Modifier.height(spacing.extraExtraLarge))
        }
    }
}

// â”€â”€â”€ WEEKLY BAR CHART COMPONENT â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/**
 * Per blueprint:
 * - 7 bars, Mon-Sun
 * - Today's bar: Primary (#ED6A5A) — coral moment
 * - Past bars: Accent/200 (#F7C1BA)
 * - Future/no-data: Neutral/200 (#E1E1E1)
 * - Bar width: 28dp, gap: 8dp, radius: ExtraSmall (4dp) top only
 * - Tapping a bar shows that day's stat below
 * - No Y-axis labels — proportional
 */
@Composable
private fun WeeklyBarChart(
    data: List<DayData>,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceSubtleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    var selectedDay by remember {
        mutableIntStateOf(data.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: 0)
    }

    val maxKm = data.maxOfOrNull { it.distanceKm } ?: 1f
    val chartHeight = 140.dp

    Column(
        modifier = modifier
    ) {
            // Bar chart area
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .semantics {
                        contentDescription = "Weekly scroll distance chart"
                    }
            ) {
                val barCount = data.size
                val totalGapWidth = (barCount - 1) * 8.dp.toPx()
                val barWidth = (size.width - totalGapWidth) / barCount
                val maxBarHeight = size.height

                data.forEachIndexed { index, day ->
                    // Calculate proportional height (minimum 4dp for empty/future)
                    val proportion = if (maxKm > 0f && day.distanceKm > 0f) {
                        day.distanceKm / maxKm
                    } else {
                        0.03f // Minimum bar height
                    }
                    val barHeight = (maxBarHeight * proportion).coerceAtLeast(4.dp.toPx())

                    val x = index * (barWidth + 8.dp.toPx())
                    val y = maxBarHeight - barHeight

                    val brush = when {
                        day.isToday -> Brush.verticalGradient(
                            colors = listOf(primaryColor, primaryColor)
                        )
                        day.isFuture || day.distanceKm == 0f -> Brush.verticalGradient(
                            colors = listOf(surfaceVariantColor, surfaceVariantColor)
                        )
                        else -> Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.4f), primaryColor.copy(alpha = 0.4f))
                        )
                    }

                    // Draw bar with rounded top corners only
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.extraSmall))

            // Day labels row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                data.forEachIndexed { index, day ->
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (day.isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedDay = index }
                    )
                }
            }

            // Selected day readout
            Spacer(modifier = Modifier.height(spacing.medium))
            val selectedData = data.getOrNull(selectedDay)
            if (selectedData != null && selectedData.distanceKm > 0f) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "${selectedData.dayLabel}: ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text = "%.1f".format(selectedData.distanceKm),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-0.03).em,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.alignByBaseline()
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "km",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            } else if (selectedData != null) {
                Text(
                    text = "${selectedData.dayLabel}: no data yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
        }
    }
}

// â”€â”€â”€ TOP APPS LIST COMPONENT â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/**
 * Per blueprint:
 * - Max 5 apps shown
 * - Leading: app icon placeholder (system-provided in production)
 * - Headline: app name (bodyLarge)
 * - Progress bar: proportional fill. Primary for #1 app, Accent/200 for others
 * - Trailing: distance (bodyLarge, tabular figures)
 */
@Composable
private fun TopAppsList(
    apps: List<AppUsage>,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val maxDistance = apps.maxOfOrNull { it.distanceKm } ?: 1f
    val totalDistance = apps.sumOf { it.distanceKm.toDouble() }.toFloat()

    Column(
        modifier = modifier.padding(vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
            apps.take(5).forEachIndexed { index, app ->
                val progress = if (maxDistance > 0f) app.distanceKm / maxDistance else 0f
                val percentage = if (totalDistance > 0f) {
                    ((app.distanceKm / totalDistance) * 100).toInt()
                } else 0

                AppRow(
                    appName = app.appName,
                    distanceKm = app.distanceKm,
                    progress = progress,
                    percentage = percentage,
                    isTopApp = index == 0
                )
            }
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
    val spacing = MaterialTheme.spacing
    val barColor = if (isTopApp) {
        MaterialTheme.colorScheme.primary // Coral for #1 app — same thread
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$appName, $percentage percent of today's total, ${"%.1f".format(distanceKm)} kilometres"
            },
        verticalArrangement = Arrangement.spacedBy(spacing.extraExtraSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f".format(distanceKm),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.02).em,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "km",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
        // Progress bar — 2dp height, full pill radius
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(50)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun InsightsScreenPreview() {
    ScrollaUILabTheme {
        InsightsScreen()
    }
}
