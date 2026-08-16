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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.scrolla.ui.components.bentoCard
import com.scrolla.ui.components.bounceClick

enum class ServiceHealthState {
    ACTIVE,
    STOPPED,
    DEGRADED,
    INTERRUPTED
}

/**
 * Screen 9 â€” Settings
 *
 * Focuses heavily on Service Health (tracking permissions and battery restrictions)
 * and Account Management (Name, Backup, Sign out, Delete).
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    serviceHealthState: ServiceHealthState = ServiceHealthState.ACTIVE,
    deviceOem: String = "Samsung",
    displayName: String = "Rohit",
    phoneLinked: Boolean = false,
    onBackClick: () -> Unit = {},
    onFixBatteryClick: () -> Unit = {},
    onEditNameClick: () -> Unit = {},
    onAddPhoneClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    onDeleteAccountClick: () -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val scrollState = rememberScrollState()

    var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isVisible = true
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
            // â”€â”€â”€ TOP APP BAR â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

            // â”€â”€â”€ SERVICE HEALTH SECTION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.fadeIn(tween(400, delayMillis = 100)) + androidx.compose.animation.slideInVertically(tween(400, delayMillis = 100), initialOffsetY = { 20 })
            ) {
                Column {
                    SettingsSectionHeader(title = ScrollaStrings.SETTINGS_HEALTH_SECTION)

                    val healthContent = when (serviceHealthState) {
                        ServiceHealthState.ACTIVE -> HealthContent(
                            title = ScrollaStrings.SETTINGS_HEALTH_ACTIVE_TITLE,
                            body = ScrollaStrings.SETTINGS_HEALTH_ACTIVE_SUBTITLE,
                            icon = Icons.Filled.CheckCircle,
                            iconTint = if (androidx.compose.foundation.isSystemInDarkTheme()) SuccessDark else SuccessLight,
                            buttonText = null
                        )
                        ServiceHealthState.STOPPED -> HealthContent(
                            title = ScrollaStrings.SETTINGS_HEALTH_STOPPED_TITLE,
                            body = ScrollaStrings.SETTINGS_HEALTH_STOPPED_BODY,
                            icon = Icons.Filled.Warning,
                            iconTint = if (androidx.compose.foundation.isSystemInDarkTheme()) ErrorDark else ErrorLight,
                            buttonText = ScrollaStrings.SETTINGS_HEALTH_STOPPED_BUTTON
                        )
                        ServiceHealthState.DEGRADED -> HealthContent(
                            title = ScrollaStrings.SETTINGS_HEALTH_DEGRADED_TITLE,
                            body = ScrollaStrings.SETTINGS_HEALTH_DEGRADED_BODY,
                            icon = Icons.Filled.Warning,
                            iconTint = if (androidx.compose.foundation.isSystemInDarkTheme()) WarningDark else WarningLight,
                            buttonText = ScrollaStrings.SETTINGS_HEALTH_DEGRADED_BUTTON
                        )
                        ServiceHealthState.INTERRUPTED -> HealthContent(
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
                        modifier = Modifier.padding(horizontal = spacing.medium)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.extraLarge))

            // â”€â”€â”€ ACCOUNT SECTION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                            value = displayName,
                            onClick = onEditNameClick
                        )
                        
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        SettingsItem(
                            label = ScrollaStrings.SETTINGS_BACKUP_LABEL,
                            value = if (phoneLinked) ScrollaStrings.SETTINGS_BACKUP_LINKED else ScrollaStrings.SETTINGS_BACKUP_ADD,
                            onClick = onAddPhoneClick,
                            valueColor = if (phoneLinked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
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
                            onClick = onSignOutClick,
                            isDestructive = false // Standard color
                        )
                        
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        SettingsItem(
                            label = ScrollaStrings.SETTINGS_DELETE_ACCOUNT,
                            onClick = onDeleteAccountClick,
                            isDestructive = true
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
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) (if (androidx.compose.foundation.isSystemInDarkTheme()) ErrorDark else ErrorLight) else MaterialTheme.colorScheme.onSurface
        )
        
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = valueColor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenActivePreview() {
    ScrollaUILabTheme {
        SettingsScreen(serviceHealthState = ServiceHealthState.ACTIVE)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenInterruptedPreview() {
    ScrollaUILabTheme {
        SettingsScreen(serviceHealthState = ServiceHealthState.INTERRUPTED)
    }
}

