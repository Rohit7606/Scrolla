package com.scrolla.ui.screens
import kotlinx.parcelize.Parcelize
import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrolla.ui.theme.ScrollaType
import com.scrolla.ui.theme.scrollaColors

sealed class ScreenRoute : Parcelable {
    @Parcelize object MainTabs : ScreenRoute()
    @Parcelize object Settings : ScreenRoute()
    @Parcelize object PersonalRecords : ScreenRoute()
    @Parcelize object HallOfFame : ScreenRoute()
    @Parcelize object ManageGroups : ScreenRoute()
    @Parcelize object JoinGroup : ScreenRoute()
    @Parcelize object CreateGroup : ScreenRoute()
    @Parcelize object WeeklyRecap : ScreenRoute()
    @Parcelize object AppBreakdown : ScreenRoute()
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

// "Board" was an abbreviation nobody says out loud, and Home/Board/
// Insights/Profile mixed three registers. These are the four things the
// user actually came for.
private val destinations = listOf(
    Destination("Today", Icons.Filled.Home, Icons.Outlined.Home, "Today, Tab 1 of 4"),
    Destination("Group", Icons.Filled.BarChart, Icons.Outlined.BarChart, "Group leaderboard, Tab 2 of 4"),
    Destination("Insights", Icons.Filled.Insights, Icons.Outlined.Insights, "Insights, Tab 3 of 4"),
    Destination("You", Icons.Filled.Person, Icons.Outlined.Person, "Your profile, Tab 4 of 4")
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
                val groupViewModel: GroupViewModel = viewModel()
                val isLoading by groupViewModel.isLoading.collectAsState()
                val errorMessage by groupViewModel.errorMessage.collectAsState()
                
                JoinGroupScreen(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onBackClick = {
                        groupViewModel.clearError()
                        popBackStack()
                    },
                    onJoinClick = { code ->
                        groupViewModel.joinGroup(code) {
                            groupViewModel.clearError()
                            popBackStack() // Go back to groups list or home on success
                        }
                    },
                    onCreateGroupClick = { 
                        groupViewModel.clearError()
                        navigateTo(ScreenRoute.CreateGroup) 
                    }
                )
            }
            is ScreenRoute.CreateGroup -> {
                val groupViewModel: GroupViewModel = viewModel()
                val isLoading by groupViewModel.isLoading.collectAsState()
                val errorMessage by groupViewModel.errorMessage.collectAsState()

                CreateGroupScreen(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onBackClick = {
                        groupViewModel.clearError()
                        popBackStack()
                    },
                    onCreateClick = { name -> 
                        groupViewModel.createGroup(name) {
                            groupViewModel.clearError()
                            popBackStack() // Go back on success
                        }
                    }
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
            // Tabs travel in the direction you moved. The old spec faded
            // every switch, which read as four unrelated pages rather
            // than one row you are moving along.
            transitionSpec = {
                val forward = targetState > initialState
                val distance = 40
                (
                    slideInHorizontally(tween(220)) { if (forward) distance else -distance } +
                        fadeIn(tween(180))
                    ) togetherWith (
                    slideOutHorizontally(tween(220)) { if (forward) -distance else distance } +
                        fadeOut(tween(120))
                    )
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
    val hairline = MaterialTheme.scrollaColors.cardBorder

    // The accent marks where you are — that is one of its four uses in
    // the whole app. The pill indicator is gone: with a coloured icon
    // AND a coloured label AND a filled container, the bar was shouting
    // three times to say one thing.
    NavigationBar(
        modifier = modifier.drawBehind {
            drawLine(
                color = hairline,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx()
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
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
                        contentDescription = dest.contentDescription,
                        modifier = Modifier.size(21.dp)
                    )
                },
                label = {
                    Text(
                        text = dest.label,
                        style = ScrollaType.Caption.copy(fontSize = 10.5.sp)
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.scrollaColors.textLow,
                    unselectedTextColor = MaterialTheme.scrollaColors.textLow,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

