package com.scrolla.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.spacing
import com.scrolla.ui.theme.SuccessDark
import com.scrolla.ui.theme.SuccessLight
import com.scrolla.ui.theme.WarningDark
import com.scrolla.ui.theme.WarningLight
import com.scrolla.ui.theme.ErrorDark
import com.scrolla.ui.theme.ErrorLight
import com.scrolla.ui.components.ScrollaHaptics
import com.scrolla.ui.components.bentoCard
import com.scrolla.ui.components.bounceClick

/**
 * Formats a health timestamp as a short local time, or the "never" copy when the
 * field has never been written. Shows the date too once the timestamp is older
 * than today, so a stale figure cannot be mistaken for one from this morning.
 */
private fun formatHealthTime(timestamp: Long?, template: String, neverText: String): String {
    if (timestamp == null || timestamp <= 0L) return neverText
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "d MMM, HH:mm"
    return String.format(
        template,
        java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    )
}

/** What the user must type to enable the delete button. Not localised on
 *  purpose: it is a fixed token, not prose. */
private const val DELETE_KEYWORD = "DELETE"

enum class UiServiceHealthState {
    ACTIVE,
    STOPPED,
    DEGRADED,
    INTERRUPTED,

    /**
     * No health row has been written yet, so nothing is known. Distinct from
     * ACTIVE on purpose: this card is the one place a user checks whether
     * tracking is working, and claiming it is fine on no evidence is the worst
     * thing it could do.
     */
    UNKNOWN
}

/**
 * Screen 9 — Settings
 *
 * Focuses heavily on Service Health (tracking permissions and battery restrictions)
 * and Account Management (Name, Backup, Sign out, Delete).
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    serviceHealthState: com.scrolla.room.ServiceHealthState? = null,
    deviceOem: String = "Samsung",
    // Blank, not a name. A default that renders plausibly is one forgotten
    // argument away from being shown to a user as their own data (P4.2).
    displayName: String = "",
    phoneLinked: Boolean = false,
    onBackClick: () -> Unit = {},
    onFixBatteryClick: () -> Unit = {},
    onEditNameClick: () -> Unit = {},
    onAddPhoneClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    onDeleteAccountClick: () -> Unit = {},
    onExportDataClick: () -> Unit = {},
    /** Clears per-app history. Scrolla keeps it indefinitely by decision, so
     *  this is the user's way to undo that. */
    onClearHistoryClick: () -> Unit = {},
    /** True while the clear is in flight. */
    isClearingHistory: Boolean = false,
    /** Non-null once a clear has finished — the outcome to report, success or
     *  failure. Silence after a destructive tap reads as "nothing happened". */
    clearHistoryResult: String? = null,
    onDismissClearHistoryResult: () -> Unit = {},
    /** True while deletion is in flight — the row must not be tappable twice. */
    isDeleting: Boolean = false,
    /** Non-null when deletion failed. Shown instead of closing silently, because
     *  a user who thinks they are deleted and is not has been actively misled. */
    deleteError: String? = null,
    onDismissDeleteError: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val scrollState = rememberScrollState()

    var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showSignOutConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showDeleteConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var deleteConfirmText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var showClearHistoryConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var hapticsOn by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(ScrollaHaptics.isEnabled(ctx))
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isVisible = true
    }

    if (deleteError != null) {
        AlertDialog(
            onDismissRequest = onDismissDeleteError,
            title = { Text(ScrollaStrings.SETTINGS_DELETE_TITLE) },
            text = { Text(deleteError) },
            confirmButton = {
                TextButton(onClick = onDismissDeleteError) {
                    Text(ScrollaStrings.ERROR_DISMISS)
                }
            }
        )
    }

    // Reports the outcome either way. A destructive action that returns to a
    // silent screen leaves the user unable to tell success from failure.
    if (clearHistoryResult != null) {
        AlertDialog(
            onDismissRequest = onDismissClearHistoryResult,
            title = { Text(ScrollaStrings.SETTINGS_CLEAR_HISTORY_TITLE) },
            text = { Text(clearHistoryResult) },
            confirmButton = {
                TextButton(onClick = onDismissClearHistoryResult) {
                    Text(ScrollaStrings.ERROR_DISMISS)
                }
            }
        )
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text(ScrollaStrings.SETTINGS_CLEAR_HISTORY_TITLE) },
            text = { Text(ScrollaStrings.SETTINGS_CLEAR_HISTORY_BODY) },
            // No type-to-confirm here, unlike account deletion. This is
            // recoverable in the sense that matters — the user keeps every
            // distance figure, their records and their group standing — so
            // gating it behind typing a word would be friction that teaches
            // people to ignore the gate on the one action that needs it.
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryConfirm = false
                    onClearHistoryClick()
                }) {
                    Text(
                        ScrollaStrings.SETTINGS_CLEAR_HISTORY_CONFIRM,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text(ScrollaStrings.SETTINGS_CLEAR_HISTORY_CANCEL)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; deleteConfirmText = "" },
            title = { Text(ScrollaStrings.SETTINGS_DELETE_TITLE) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                    Text(ScrollaStrings.SETTINGS_DELETE_BODY)
                    // SETTINGS_DELETE_INPUT_HINT has existed since the copy was
                    // first written and had never been rendered. A type-to-confirm
                    // gate is the right weight for an action with no undo, and it
                    // was already the intended design.
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        singleLine = true,
                        label = { Text(ScrollaStrings.SETTINGS_DELETE_INPUT_HINT) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteConfirmText.trim().equals(DELETE_KEYWORD, ignoreCase = false),
                    onClick = {
                        showDeleteConfirm = false
                        deleteConfirmText = ""
                        onDeleteAccountClick()
                    }
                ) {
                    Text(
                        ScrollaStrings.SETTINGS_DELETE_CONFIRM,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            // The safe choice says what it does. "Cancel" next to a deletion
            // prompt is ambiguous about which thing is being cancelled.
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; deleteConfirmText = "" }) {
                    Text(ScrollaStrings.SETTINGS_DELETE_CANCEL)
                }
            }
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(ScrollaStrings.SETTINGS_SIGN_OUT_TITLE) },
            text = { Text(ScrollaStrings.SETTINGS_SIGN_OUT_BODY) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    onSignOutClick()
                }) {
                    Text(ScrollaStrings.SETTINGS_SIGN_OUT)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
            // ─── TOP APP BAR ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = spacing.extraSmall, // tighter for icon button
                        end = spacing.medium,
                        top = spacing.small,
                        bottom = spacing.small
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
                Spacer(modifier = Modifier.width(spacing.extraSmall))
                Text(
                    text = ScrollaStrings.SETTINGS_TITLE,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // ─── SERVICE HEALTH SECTION ──────────────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 100)) + androidx.compose.animation.slideInVertically(tween(400, delayMillis = 100), initialOffsetY = { 20 })
            ) {
                Column {
                    SettingsSectionHeader(title = ScrollaStrings.SETTINGS_HEALTH_SECTION)

                    val uiHealthState = when {
                        serviceHealthState == null -> UiServiceHealthState.UNKNOWN
                        // Now checks the accessibility master switch too, not just
                        // the enabled-services list — a crashed service stays in
                        // that list, which is how a dead service read as healthy
                        // for 9.5 hours on 2026-08-25. This branch is what catches
                        // that case now.
                        !serviceHealthState.isAccessibilityServiceEnabled -> UiServiceHealthState.STOPPED
                        // A `lastRoomFlushTimestamp == 0L -> UNKNOWN` guard used to
                        // sit here, because isServiceRunning was only ever set true
                        // by a successful flush — so a service enabled seconds ago
                        // had it false through no fault of its own, and INTERRUPTED
                        // accused the OS of killing something that had simply not
                        // had anything to write yet. onServiceConnected() now sets
                        // the flag directly, so the flag means what it says and the
                        // guard would only hide real INTERRUPTED states.
                        !serviceHealthState.isServiceRunning -> UiServiceHealthState.INTERRUPTED
                        serviceHealthState.degradedReason != null -> UiServiceHealthState.DEGRADED
                        else -> UiServiceHealthState.ACTIVE
                    }

                    val healthContent = when (uiHealthState) {
                        UiServiceHealthState.ACTIVE -> HealthContent(
                            title = ScrollaStrings.SETTINGS_HEALTH_ACTIVE_TITLE,
                            body = ScrollaStrings.SETTINGS_HEALTH_ACTIVE_SUBTITLE,
                            icon = Icons.Filled.CheckCircle,
                            iconTint = if (androidx.compose.foundation.isSystemInDarkTheme()) SuccessDark else SuccessLight,
                            buttonText = ScrollaStrings.SETTINGS_HEALTH_ACTIVE_BUTTON
                        )
                        UiServiceHealthState.STOPPED -> HealthContent(
                            title = ScrollaStrings.SETTINGS_HEALTH_STOPPED_TITLE,
                            body = ScrollaStrings.SETTINGS_HEALTH_STOPPED_BODY,
                            icon = Icons.Filled.Warning,
                            iconTint = if (androidx.compose.foundation.isSystemInDarkTheme()) ErrorDark else ErrorLight,
                            buttonText = ScrollaStrings.SETTINGS_HEALTH_STOPPED_BUTTON
                        )
                        UiServiceHealthState.DEGRADED -> HealthContent(
                            title = ScrollaStrings.SETTINGS_HEALTH_DEGRADED_TITLE,
                            body = ScrollaStrings.SETTINGS_HEALTH_DEGRADED_BODY,
                            icon = Icons.Filled.Warning,
                            iconTint = if (androidx.compose.foundation.isSystemInDarkTheme()) WarningDark else WarningLight,
                            buttonText = ScrollaStrings.SETTINGS_HEALTH_DEGRADED_BUTTON
                        )
                        UiServiceHealthState.UNKNOWN -> HealthContent(
                            title = ScrollaStrings.SETTINGS_HEALTH_UNKNOWN_TITLE,
                            body = ScrollaStrings.SETTINGS_HEALTH_UNKNOWN_BODY,
                            icon = Icons.Outlined.BatteryAlert,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            buttonText = ScrollaStrings.SETTINGS_HEALTH_UNKNOWN_BUTTON
                        )
                        UiServiceHealthState.INTERRUPTED -> HealthContent(
                            title = ScrollaStrings.SETTINGS_HEALTH_INTERRUPTED_TITLE,
                            body = String.format(ScrollaStrings.SETTINGS_HEALTH_INTERRUPTED_BODY_TEMPLATE, deviceOem),
                            icon = Icons.Outlined.BatteryAlert,
                            iconTint = if (androidx.compose.foundation.isSystemInDarkTheme()) WarningDark else WarningLight,
                            buttonText = ScrollaStrings.SETTINGS_HEALTH_INTERRUPTED_BUTTON
                        )
                    }

                    HealthCard(
                        content = healthContent,
                        onButtonClick = onFixBatteryClick,
                        // Last *recorded* comes first, ahead of last synced. Sync
                        // freshness says Firestore is reachable; it says nothing about
                        // whether the accessibility service is still alive. On
                        // 2026-08-25 the service crashed at 09:34 and this card still
                        // read "Tracking is active" nine hours later, because every
                        // signal it consults is a latch that is only ever set true.
                        // A visible "last recorded 09:34" is the one thing on the card
                        // that can contradict its own headline.
                        footnote = listOfNotNull(
                            formatHealthTime(
                                serviceHealthState?.lastRoomFlushTimestamp,
                                ScrollaStrings.SETTINGS_HEALTH_LAST_RECORDED,
                                ScrollaStrings.SETTINGS_HEALTH_NEVER_RECORDED
                            ),
                            formatHealthTime(
                                serviceHealthState?.lastFirestoreSyncTimestamp,
                                ScrollaStrings.SETTINGS_HEALTH_LAST_SYNC,
                                ScrollaStrings.SETTINGS_HEALTH_NEVER_SYNCED
                            )
                        ).joinToString(" · "),
                        modifier = Modifier.padding(horizontal = spacing.medium)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.extraLarge))

            // ─── ACCOUNT SECTION ─────────────────────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 200)) + androidx.compose.animation.slideInVertically(tween(400, delayMillis = 200), initialOffsetY = { 20 })
            ) {
                Column {
                    SettingsSectionHeader(title = ScrollaStrings.SETTINGS_ACCOUNT_SECTION)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.medium)
                            .bentoCard(padding = 0.dp) // Tightly pack the items
                    ) {
                        SettingsItem(
                            label = ScrollaStrings.SETTINGS_DISPLAY_NAME_LABEL,
                            // Real data when Auth has it. Blank is possible —
                            // FirebaseUser.displayName can be null even for a
                            // Google account — and that is the only case where
                            // "not yet available" is a true statement here.
                            value = displayName.ifBlank { ScrollaStrings.SETTINGS_NOT_YET_AVAILABLE },
                            onClick = onEditNameClick,
                            enabled = false
                        )
                        
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        SettingsItem(
                            label = ScrollaStrings.SETTINGS_BACKUP_LABEL,
                            // "Add phone number" is an offer, and S1.B4 is not
                            // built, so offering it on a dead row is the lie the
                            // disabled state was there to avoid. Say the feature
                            // is missing until linking actually exists.
                            value = if (phoneLinked) ScrollaStrings.SETTINGS_BACKUP_LINKED else ScrollaStrings.SETTINGS_NOT_YET_AVAILABLE,
                            onClick = onAddPhoneClick,
                            valueColor = if (phoneLinked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            enabled = false
                        )
                    }

                    Spacer(modifier = Modifier.height(spacing.large))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.medium)
                            .bentoCard(padding = 0.dp)
                    ) {
                        SettingsItem(
                            label = ScrollaStrings.SETTINGS_SIGN_OUT,
                            // Confirm first: signing out is one tap from a
                            // destructive-feeling outcome, and the copy for the
                            // dialog was already written and never shown.
                            onClick = { showSignOutConfirm = true },
                            isDestructive = false // Standard color
                        )
                        
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        SettingsItem(
                            label = ScrollaStrings.SETTINGS_HAPTICS_LABEL,
                            value = if (hapticsOn) {
                                ScrollaStrings.SETTINGS_HAPTICS_ON
                            } else {
                                ScrollaStrings.SETTINGS_HAPTICS_OFF
                            },
                            // Scrolla drives the vibrator directly rather than
                            // going through touch feedback, so the system's
                            // touch-feedback switch does not turn these off.
                            // Bypassing that setting without offering an
                            // alternative would be worse than respecting it,
                            // so this is the alternative.
                            onClick = {
                                val next = !hapticsOn
                                ScrollaHaptics.setEnabled(ctx, next)
                                hapticsOn = next
                                if (next) ScrollaHaptics.confirm(ctx)
                            }
                        )

                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        SettingsItem(
                            // Sits directly above Delete on purpose: the moment
                            // someone is looking for the way out is the moment
                            // they might want to take their data with them.
                            label = ScrollaStrings.SETTINGS_EXPORT_LABEL,
                            onClick = onExportDataClick
                        )

                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        SettingsItem(
                            // Between Export and Delete deliberately: the three
                            // rows form a ladder of increasing finality, and a
                            // user who wants less data held about them should
                            // meet the smaller option before the nuclear one.
                            label = if (isClearingHistory) {
                                ScrollaStrings.SETTINGS_CLEAR_HISTORY_IN_PROGRESS
                            } else {
                                ScrollaStrings.SETTINGS_CLEAR_HISTORY_LABEL
                            },
                            onClick = { showClearHistoryConfirm = true },
                            isDestructive = true,
                            enabled = !isClearingHistory
                        )

                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        SettingsItem(
                            label = if (isDeleting) {
                                ScrollaStrings.SETTINGS_DELETE_IN_PROGRESS
                            } else {
                                ScrollaStrings.SETTINGS_DELETE_ACCOUNT
                            },
                            // Was `enabled = false` and had never done anything.
                            // For an app built on an accessibility service this
                            // is the trust affordance, not a nice-to-have: you
                            // cannot ask someone to let you watch every scroll
                            // they make and then offer no way out.
                            onClick = { showDeleteConfirm = true },
                            isDestructive = true,
                            enabled = !isDeleting
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.extraExtraLarge))
        }
    }
}

private data class HealthContent(
    val title: String,
    val body: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconTint: androidx.compose.ui.graphics.Color,
    val buttonText: String?
)

@Composable
private fun HealthCard(
    content: HealthContent,
    onButtonClick: () -> Unit,
    footnote: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bentoCard()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = content.icon,
                contentDescription = null,
                tint = content.iconTint,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = content.body,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (footnote != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = footnote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                if (content.buttonText != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = onButtonClick,
                        modifier = Modifier.padding(start = 0.dp)
                    ) {
                        Text(
                            text = content.buttonText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 0.05.em,
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
    )
}

@Composable
private fun SettingsItem(
    label: String,
    value: String? = null,
    onClick: () -> Unit,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isDestructive: Boolean = false,
    /** False for features that do not exist yet: the row greys out and stops
     *  responding to taps, rather than looking live and doing nothing. */
    enabled: Boolean = true
) {
    val rowAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.bounceClick(onClick = onClick) else Modifier)
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = (if (isDestructive) (if (androidx.compose.foundation.isSystemInDarkTheme()) ErrorDark else ErrorLight) else MaterialTheme.colorScheme.onSurface)
                .copy(alpha = rowAlpha)
        )

        // `enabled` controls whether the row can be *tapped*, and says nothing
        // about whether the value exists. It used to overwrite the value with
        // "Not yet available" whenever the row was disabled, so Settings told a
        // signed-in user their display name was unavailable while Auth was
        // holding it — a confident false statement about data the app had, which
        // is the failure this app's honesty rule exists to prevent. A row that
        // genuinely has nothing to show now says so at the call site, where the
        // absence is actually known.
        Text(
            text = value ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) valueColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ScrollaUILabTheme {
        SettingsScreen(
            serviceHealthState = com.scrolla.room.ServiceHealthState(
                isServiceRunning = true,
                isAccessibilityServiceEnabled = true,
                lastEventTimestamp = 0,
                lastRoomFlushTimestamp = 0,
                lastFirestoreSyncTimestamp = 0,
                degradedReason = null
            ),
            deviceOem = "Samsung",
            displayName = "Rohit",
            phoneLinked = true
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenDegradedPreview() {
    ScrollaUILabTheme {
        SettingsScreen(
            serviceHealthState = com.scrolla.room.ServiceHealthState(
                isServiceRunning = true,
                isAccessibilityServiceEnabled = true,
                lastEventTimestamp = 0,
                lastRoomFlushTimestamp = 0,
                lastFirestoreSyncTimestamp = 0,
                degradedReason = "Room write failed"
            ),
            deviceOem = "Oppo",
            displayName = "Rohit",
            phoneLinked = false
        )
    }
}
