package com.scrolla.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.scrolla.ui.components.bentoCard
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.spacing

/**
 * Screen 13 - Privacy (BUILD_PLAN 1.6).
 *
 * Mandatory for an app built on an AccessibilityService, and more so now that
 * there is a cloud backup and, since 2026-09-14, crash reporting. Until this
 * existed the whole retention promise lived in one caption in Settings.
 *
 * **The rule this screen is written to: state facts, not reassurances.** Every
 * claim here is checkable against the code, and the important one is checkable
 * by the user against their own phone - `canRetrieveWindowContent="false"` is
 * in `accessibility_service_config.xml`, so Android never delivers screen
 * content to Scrolla at all. That is worth more than any sentence promising we
 * would not look. An app that asks to observe every scroll a person makes does
 * not get to be vague about what it sees.
 *
 * Read-only by design. Every control it mentions already exists in Settings,
 * and putting a destructive action on the page that explains it would give the
 * same button two homes.
 */
@Composable
fun PrivacyScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(scrollState)
        ) {
            // --- TOP APP BAR ---------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.spacing.extraSmall,
                        end = MaterialTheme.spacing.medium,
                        top = MaterialTheme.spacing.small,
                        bottom = MaterialTheme.spacing.small
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate up",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = ScrollaStrings.PRIVACY_TITLE,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // --- THE SHORT VERSION ---------------------------------------
            // First, and deliberately the whole answer. Someone who reads one
            // card and leaves should still have been told the truth.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.medium)
                    .bentoCard(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                // Two lines, two Texts. The second sentence is the one people
                // actually want answered before granting an accessibility
                // service, so it gets its own line at full weight rather than
                // trailing off wherever the screen width happens to wrap it.
                Text(
                    text = ScrollaStrings.PRIVACY_SUMMARY_HEADLINE_1,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = ScrollaStrings.PRIVACY_SUMMARY_HEADLINE_2,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = ScrollaStrings.PRIVACY_SUMMARY_BODY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))

            PrivacySection(title = ScrollaStrings.PRIVACY_RECEIVES_SECTION) {
                PrivacyBody(ScrollaStrings.PRIVACY_RECEIVES_BODY)
            }

            PrivacySection(title = ScrollaStrings.PRIVACY_CANNOT_SECTION) {
                PrivacyBullet(ScrollaStrings.PRIVACY_CANNOT_1)
                PrivacyBullet(ScrollaStrings.PRIVACY_CANNOT_2)
                PrivacyBullet(ScrollaStrings.PRIVACY_CANNOT_3)
                PrivacyBullet(ScrollaStrings.PRIVACY_CANNOT_4)
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
                // The load-bearing sentence on this screen: it turns four
                // claims into something the user can check without us.
                Text(
                    text = ScrollaStrings.PRIVACY_CANNOT_FOOTNOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PrivacySection(title = ScrollaStrings.PRIVACY_ON_DEVICE_SECTION) {
                PrivacyBody(ScrollaStrings.PRIVACY_ON_DEVICE_BODY)
            }

            PrivacySection(title = ScrollaStrings.PRIVACY_LEAVES_SECTION) {
                PrivacyEntry(
                    title = ScrollaStrings.PRIVACY_LEAVES_1_TITLE,
                    body = ScrollaStrings.PRIVACY_LEAVES_1_BODY
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                PrivacyEntry(
                    title = ScrollaStrings.PRIVACY_LEAVES_2_TITLE,
                    body = ScrollaStrings.PRIVACY_LEAVES_2_BODY
                )
                PrivacyEntry(
                    title = ScrollaStrings.PRIVACY_LEAVES_3_TITLE,
                    body = ScrollaStrings.PRIVACY_LEAVES_3_BODY
                )
            }

            PrivacySection(title = ScrollaStrings.PRIVACY_CRASH_SECTION) {
                PrivacyBody(ScrollaStrings.PRIVACY_CRASH_BODY)
            }

            PrivacySection(title = ScrollaStrings.PRIVACY_RETENTION_SECTION) {
                PrivacyBody(ScrollaStrings.PRIVACY_RETENTION_BODY)
            }

            PrivacySection(title = ScrollaStrings.PRIVACY_REMOVE_SECTION) {
                PrivacyBody(ScrollaStrings.PRIVACY_REMOVE_BODY)
            }

            // Named here rather than left to be discovered mid-payment.
            // Scrolla does not work around the block - see BUILD_PLAN 1.5.
            PrivacySection(title = ScrollaStrings.PRIVACY_PAYMENT_SECTION) {
                PrivacyBody(ScrollaStrings.PRIVACY_PAYMENT_BODY)
            }

            Text(
                text = ScrollaStrings.PRIVACY_FOOTER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.medium
                )
            )

            Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraExtraLarge))
        }
    }
}

/**
 * A titled block. Uses the same header treatment as Settings so the two screens
 * read as one app rather than two authors.
 */
@Composable
private fun PrivacySection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 0.05.em,
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        modifier = Modifier.padding(
            horizontal = MaterialTheme.spacing.medium,
            vertical = MaterialTheme.spacing.small
        )
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium)
            .bentoCard()
    ) {
        content()
    }
    Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
}

/** Body copy. One place, so the colour and style cannot drift between blocks. */
@Composable
private fun PrivacyBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * A dot and a line, used only for the "cannot see" list - where the items are
 * short and the length of the list is itself the point.
 */
@Composable
private fun PrivacyBullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** A titled sub-item, for the two things that genuinely leave the device. */
@Composable
private fun PrivacyEntry(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraSmall))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PrivacyScreenPreview() {
    ScrollaUILabTheme {
        PrivacyScreen()
    }
}
