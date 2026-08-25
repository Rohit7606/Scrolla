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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

/**
 * Profile — the archive.
 *
 * This screen spends no accent at all, and that is deliberate: coral
 * means you, here, now, and nothing here is now. It is the proof that
 * the rule is a rule rather than a preference.
 */
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    displayName: String = "Rohit",
    memberSinceLabel: String? = "Tracking since 6 July",
    /** Null until the sensor has produced at least one full day. */
    personalBestKm: Float? = 0.4f,
    personalBestRelativeDate: String? = "12 July",
    sevenDayAvgKm: Float? = 3.1f,
    previousSevenDayAvgKm: Float? = 3.6f,
    /** Null until the group has a record to measure against. */
    hallOfFameGapKm: Float? = 1.9f,
    isRecordHolder: Boolean = false,
    groupCount: Int = 2,
    primaryGroupName: String? = "College Friends",
    /** Non-null when the group read failed. Without this the row fell back to
     *  "not in any groups yet", which is a false statement rather than a
     *  missing one. */
    errorMessage: String? = null,
    onSettingsClick: () -> Unit = {},
    onPersonalRecordsClick: () -> Unit = {},
    onHallOfFameClick: () -> Unit = {},
    onManageGroupsClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.scrollaColors
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(scrollState)
    ) {

        // ─── IDENTITY ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.screenInset, end = spacing.extraSmall, top = spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, colors.cardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        style = ScrollaType.FigureSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = displayName,
                        style = ScrollaType.Display.copy(
                            fontSize = ScrollaType.Display.fontSize * 0.875f
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = memberSinceLabel ?: ScrollaStrings.PROFILE_TRACKING_SINCE_UNKNOWN,
                        style = ScrollaType.Caption,
                        color = colors.textLow
                    )
                }
            }

            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(20.dp),
                    tint = colors.textLow
                )
            }
        }

        // ─── RECORDS ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = spacing.sectionGap),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionLabel(ScrollaStrings.RECORDS_TITLE)

            // IntrinsicSize.Min keeps the pair the same height when one of
            // them falls back to a wrapping empty state.
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(spacing.cardGap)
            ) {
                ScrollaCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onPersonalRecordsClick
                ) {
                    val best = personalBestKm?.takeIf { it > 0f }
                    StatBlock(
                        label = ScrollaStrings.RECORDS_BEST_DAY_LABEL,
                        value = best?.let { DistanceFormatter.formatDisplayValue(it) },
                        unit = DistanceFormatter.formatDisplayUnit(best ?: 0f),
                        footnote = if (best != null) {
                            personalBestRelativeDate
                        } else {
                            ScrollaStrings.PROFILE_PERSONAL_BEST_EMPTY
                        }
                    )
                }

                ScrollaCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onPersonalRecordsClick
                ) {
                    val trend = if (sevenDayAvgKm != null && previousSevenDayAvgKm != null) {
                        if (sevenDayAvgKm <= previousSevenDayAvgKm) {
                            "down from ${DistanceFormatter.formatDisplayValue(previousSevenDayAvgKm)}"
                        } else {
                            "up from ${DistanceFormatter.formatDisplayValue(previousSevenDayAvgKm)}"
                        }
                    } else {
                        null
                    }
                    val improving = sevenDayAvgKm == null || previousSevenDayAvgKm == null ||
                        sevenDayAvgKm <= previousSevenDayAvgKm

                    StatBlock(
                        label = ScrollaStrings.RECORDS_SEVEN_DAY_LABEL,
                        value = sevenDayAvgKm?.let { DistanceFormatter.formatDisplayValue(it) },
                        unit = DistanceFormatter.formatDisplayUnit(sevenDayAvgKm ?: 0f),
                        footnote = trend ?: if (sevenDayAvgKm == null) {
                            ScrollaStrings.PROFILE_PERSONAL_BEST_EMPTY
                        } else {
                            null
                        },
                        footnoteColor = if (improving) colors.improving else colors.worsening
                    )
                }
            }
        }

        // ─── HALL OF FAME + GROUPS ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenInset)
                .padding(top = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap)
        ) {

            ScrollaCard(onClick = onHallOfFameClick) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionLabel(ScrollaStrings.PROFILE_HALL_OF_FAME_LINK)
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.textLow
                        )
                    }
                    Text(
                        text = when {
                            isRecordHolder -> ScrollaStrings.PROFILE_HALL_OF_FAME_RECORD_HOLDER
                            hallOfFameGapKm != null && hallOfFameGapKm > 0f -> String.format(
                                ScrollaStrings.HALL_OF_FAME_PROGRESS_TEMPLATE,
                                DistanceFormatter.formatDistance(hallOfFameGapKm)
                            )
                            else -> ScrollaStrings.PROFILE_HALL_OF_FAME_EMPTY
                        },
                        style = ScrollaType.Body,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!isRecordHolder && hallOfFameGapKm != null && hallOfFameGapKm > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.17f)
                                    .height(4.dp)
                                    .clip(PillShape)
                                    .background(colors.textLow)
                            )
                        }
                    }
                }
            }

            ScrollaCard(onClick = onManageGroupsClick) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel(ScrollaStrings.GROUP_SWITCHER_TITLE)
                        Text(
                            text = when {
                                errorMessage != null -> ScrollaStrings.ERROR_GROUPS_UNAVAILABLE
                                primaryGroupName != null ->
                                    "$groupCount groups · $primaryGroupName on widget"
                                else -> ScrollaStrings.PROFILE_NO_GROUPS
                            },
                            style = ScrollaType.Body,
                            color = if (errorMessage != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
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
private fun StatBlock(
    label: String,
    value: String?,
    unit: String = "km",
    footnote: String?,
    footnoteColor: androidx.compose.ui.graphics.Color = MaterialTheme.scrollaColors.textLow
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(label)
        if (value != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = ScrollaType.FigureMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = unit,
                    style = ScrollaType.Caption,
                    color = MaterialTheme.scrollaColors.textLow,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
        if (footnote != null) {
            Text(
                text = footnote,
                style = ScrollaType.Caption,
                color = footnoteColor
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
