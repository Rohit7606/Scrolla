package com.scrolla.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

sealed class ScreenRoute {
    object MainTabs : ScreenRoute()
    object Settings : ScreenRoute()
    object PersonalRecords : ScreenRoute()
    object HallOfFame : ScreenRoute()
    object ManageGroups : ScreenRoute()
    object JoinGroup : ScreenRoute()
    object CreateGroup : ScreenRoute()
    object WeeklyRecap : ScreenRoute()
    object AppBreakdown : ScreenRoute()
}

/**
 * Primary destinations for Scrolla's Navigation Bar.
 */
private data class Destination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String
)

private val destinations = listOf(
    Destination("Home", Icons.Filled.Home, Icons.Outlined.Home, "Home, Tab 1 of 4"),
    Destination("Board", Icons.Filled.BarChart, Icons.Outlined.BarChart, "Leaderboard, Tab 2 of 4"),
    Destination("Insights", Icons.Filled.Insights, Icons.Outlined.Insights, "Insights, Tab 3 of 4"),
    Destination("Profile", Icons.Filled.Person, Icons.Outlined.Person, "Profile, Tab 4 of 4")
)

@Composable
fun MainShell(modifier: Modifier = Modifier) {
    var routeStack by rememberSaveable { mutableStateOf(listOf<ScreenRoute>(ScreenRoute.MainTabs)) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    
    val currentRoute = routeStack.last()

    val navigateTo: (ScreenRoute) -> Unit = { route ->
        routeStack = routeStack + route
    }
    val popBackStack: () -> Unit = {
        if (routeStack.size > 1) {
            routeStack = routeStack.dropLast(1)
        }
    }

    // Handle physical/system back button
    BackHandler(enabled = routeStack.size > 1) {
        popBackStack()
    }

    AnimatedContent(
        targetState = currentRoute,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith
                fadeOut(animationSpec = tween(150))
        },
        label = "route_content"
    ) { route ->
        when (route) {
            is ScreenRoute.MainTabs -> {
                MainTabsScreen(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onSettingsClick = { navigateTo(ScreenRoute.Settings) },
                    onPersonalRecordsClick = { navigateTo(ScreenRoute.PersonalRecords) },
                    onHallOfFameClick = { navigateTo(ScreenRoute.HallOfFame) },
                    onManageGroupsClick = { navigateTo(ScreenRoute.ManageGroups) },
                    onAppBreakdownClick = { navigateTo(ScreenRoute.AppBreakdown) },
                    onShowRecapClick = { navigateTo(ScreenRoute.WeeklyRecap) }
                )
            }
            is ScreenRoute.Settings -> {
                SettingsScreen(onBackClick = popBackStack)
            }
            is ScreenRoute.PersonalRecords -> {
                PersonalRecordsScreen(onBackClick = popBackStack)
            }
            is ScreenRoute.HallOfFame -> {
                HallOfFameScreen(onBackClick = popBackStack)
            }
            is ScreenRoute.ManageGroups -> {
                GroupSwitcherScreen(
                    onBackClick = popBackStack,
                    onJoinAnotherClick = { navigateTo(ScreenRoute.JoinGroup) },
                    onGroupClick = { popBackStack() }
                )
            }
            is ScreenRoute.JoinGroup -> {
                JoinGroupScreen(
                    onBackClick = popBackStack,
                    onJoinClick = { popBackStack() }, // Mock joining success
                    onCreateGroupClick = { navigateTo(ScreenRoute.CreateGroup) }
                )
            }
            is ScreenRoute.CreateGroup -> {
                CreateGroupScreen(
                    onBackClick = popBackStack,
                    onCreateClick = { popBackStack() } // Mock creating success
                )
            }
            is ScreenRoute.WeeklyRecap -> {
                WeeklyRecapScreen(
                    onSkipClick = popBackStack,
                    onShareClick = popBackStack
                )
            }
            is ScreenRoute.AppBreakdown -> {
                AppBreakdownScreen(onBackClick = popBackStack)
            }
        }
    }
}

@Composable
private fun MainTabsScreen(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onPersonalRecordsClick: () -> Unit,
    onHallOfFameClick: () -> Unit,
    onManageGroupsClick: () -> Unit,
    onAppBreakdownClick: () -> Unit,
    onShowRecapClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            ScrollaNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith
                    fadeOut(animationSpec = tween(150))
            },
            label = "tab_content"
        ) { tab ->
            when (tab) {
                0 -> HomeScreen(
                    onSettingsClick = onSettingsClick,
                    onRankChipClick = { onTabSelected(1) } // Switch to leaderboard
                )
                1 -> LeaderboardScreen(
                    onHallOfFameClick = onHallOfFameClick
                )
                2 -> InsightsScreen(
                    onAppBreakdownClick = onAppBreakdownClick,
                    onShowRecapClick = onShowRecapClick
                )
                3 -> ProfileScreen(
                    onSettingsClick = onSettingsClick,
                    onPersonalRecordsClick = onPersonalRecordsClick,
                    onHallOfFameClick = onHallOfFameClick,
                    onManageGroupsClick = onManageGroupsClick
                )
            }
        }
    }
}

@Composable
private fun ScrollaNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        destinations.forEachIndexed { index, dest ->
            val selected = selectedTab == index
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = dest.contentDescription
                    )
                },
                label = {
                    Text(
                        text = dest.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

