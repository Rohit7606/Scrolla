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
import androidx.compose.material.icons.filled.Check
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
import com.scrolla.ui.components.ScrollaPrimaryButton
import com.scrolla.ui.theme.ScrollaUILabTheme
import com.scrolla.ui.theme.spacing
import com.scrolla.ui.components.bentoCard
import com.scrolla.ui.components.bounceClick

data class GroupInfo(
    val id: String,
    val name: String,
    val memberCount: Int,
    val isWidgetGroup: Boolean
)

/**
 * Screen 10 — Group Switcher
 */
@Composable
fun GroupSwitcherScreen(
    modifier: Modifier = Modifier,
    groups: List<GroupInfo> = listOf(
        GroupInfo("1", "College Friends", 5, true),
        GroupInfo("2", "Family", 4, false)
    ),
    activeGroupId: String = "1",
    onBackClick: () -> Unit = {},
    onGroupClick: (String) -> Unit = {},
    onJoinAnotherClick: () -> Unit = {}
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
        ) {
            // ─── TOP APP BAR ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = spacing.extraSmall,
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
                    text = ScrollaStrings.GROUP_SWITCHER_TITLE,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // ─── CONTENT ──────────────────────────────────────────────
            if (groups.isEmpty()) {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = spacing.medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = ScrollaStrings.GROUP_SWITCHER_EMPTY,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(spacing.large))
                    ScrollaPrimaryButton(
                        text = ScrollaStrings.GROUP_SWITCHER_EMPTY_BUTTON,
                        onClick = onJoinAnotherClick,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                }
            } else {
                // List of groups
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    groups.forEachIndexed { index, group ->
                        val isActive = group.id == activeGroupId
                        
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isVisible,
                            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300, delayMillis = 100 + (index * 50))) + 
                                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(300, delayMillis = 100 + (index * 50)), initialOffsetY = { 20 })
                        ) {
                            GroupCard(
                                group = group,
                                isActive = isActive,
                                onClick = { onGroupClick(group.id) },
                                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.large))

                    // Join Another Button (styled like a text button but larger)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isVisible,
                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400, delayMillis = 100 + (groups.size * 50))) + 
                                androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(400, delayMillis = 100 + (groups.size * 50)), initialOffsetY = { 20 })
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onJoinAnotherClick)
                                .padding(horizontal = spacing.medium, vertical = spacing.medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ScrollaStrings.GROUP_SWITCHER_ADD,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(spacing.extraExtraLarge))
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: GroupInfo,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    val contentColor = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .bentoCard(
                backgroundColor = containerColor,
                borderColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha=0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha=0.05f)
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = contentColor
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "${group.memberCount} members" + if (group.isWidgetGroup) " · ${ScrollaStrings.GROUP_SWITCHER_WIDGET_LABEL}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) contentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isActive) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Active group",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupSwitcherScreenPreview() {
    ScrollaUILabTheme {
        GroupSwitcherScreen()
    }
}

