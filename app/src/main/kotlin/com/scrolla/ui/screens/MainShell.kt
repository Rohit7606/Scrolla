package com.scrolla.ui.screens
import kotlinx.parcelize.Parcelize
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import com.scrolla.device.BatteryWhitelistHelper
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
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
import com.scrolla.model.DistanceFormatter
import com.scrolla.ui.components.RefreshOnResume
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
fun MainShell(
    modifier: Modifier = Modifier,
    /** Signing out clears the Firebase session; without this the shell stayed on
     *  screen showing a logged-in UI backed by a dead session. */
    onSignedOut: () -> Unit = {}
) {
    val context = LocalContext.current
    var routeStack by rememberSaveable { mutableStateOf(listOf<ScreenRoute>(ScreenRoute.MainTabs)) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Survives rotation so a freshly created code is never lost before it is shared.
    var createdGroupCode by rememberSaveable { mutableStateOf<String?>(null) }

    // S2.3 — sync on the interval timer for as long as the shell is composed,
    // and again whenever the app returns to foreground.
    val syncViewModel: SyncViewModel = viewModel()
    LaunchedEffect(Unit) { syncViewModel.runPeriodicSync() }
    RefreshOnResume { syncViewModel.syncNow("foreground") }

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
                val settingsViewModel: SettingsViewModel = viewModel()
                val settingsState by settingsViewModel.uiState.collectAsState()

                SettingsScreen(
                    serviceHealthState = settingsState.serviceHealth,
                    deviceOem = settingsState.deviceOem,
                    displayName = settingsState.displayName,
                    phoneLinked = settingsState.phoneLinked,
                    onBackClick = popBackStack,
                    onSignOutClick = {
                        settingsViewModel.signOut(context)
                        onSignedOut()
                    },
                    onFixBatteryClick = {
                        // The health card's action button was inert, which is the
                        // worst place for a dead control: it only appears when
                        // tracking is already broken. Route each failure to the
                        // screen that actually fixes it.
                        val health = settingsState.serviceHealth
                        // No health row means tracking has never run, so the
                        // useful destination is Accessibility, not battery.
                        if (health == null || !health.isAccessibilityServiceEnabled) {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else if (!BatteryWhitelistHelper().openBatterySettings(context)) {
                            // openBatterySettings returns false when no OEM intent
                            // resolved. Fall back to this app's settings page so the
                            // button never does nothing.
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }
            is ScreenRoute.PersonalRecords -> {
                val recordsViewModel: PersonalRecordsViewModel = viewModel()
                val recordsState by recordsViewModel.uiState.collectAsState()

                PersonalRecordsScreen(
                    hasData = recordsState.hasData,
                    bestDayKm = recordsState.bestDayKm,
                    bestDayDate = recordsState.bestDayDate,
                    bestAvgKm = recordsState.bestAvgKm,
                    bestAvgDate = recordsState.bestAvgDate,
                    onBackClick = popBackStack
                )
            }
            is ScreenRoute.HallOfFame -> {
                val fameViewModel: HallOfFameViewModel = viewModel()
                val fameState by fameViewModel.uiState.collectAsState()

                HallOfFameScreen(
                    hasRecord = fameState.hasRecord,
                    recordHolderName = fameState.recordHolderName,
                    recordDistanceKm = fameState.recordDistanceKm,
                    recordDate = fameState.recordDate,
                    isCurrentUserHolder = fameState.isCurrentUserHolder,
                    gapToRecordKm = fameState.gapToRecordKm,
                    onBackClick = popBackStack
                )
            }
            is ScreenRoute.ManageGroups -> {
                val leaderboardViewModel: LeaderboardViewModel = viewModel()
                val boardState by leaderboardViewModel.uiState.collectAsState()

                // A group created or joined moments ago must be here — the cached
                // list is why it previously took a screen switch to appear.
                LaunchedEffect(Unit) { leaderboardViewModel.refresh() }

                GroupSwitcherScreen(
                    groups = boardState.groups.map {
                        GroupInfo(
                            id = it.groupId,
                            name = it.groupName,
                            memberCount = it.memberCount,
                            isWidgetGroup = it.isPrimary
                        )
                    },
                    activeGroupId = boardState.activeGroup?.groupId.orEmpty(),
                    onBackClick = popBackStack,
                    onJoinAnotherClick = { navigateTo(ScreenRoute.JoinGroup) },
                    onCreateGroupClick = { navigateTo(ScreenRoute.CreateGroup) },
                    onSetWidgetGroupClick = { group ->
                        leaderboardViewModel.setPrimaryGroup(group.id)
                    },
                    onShareClick = { group ->
                        // The group's document id is its join code, so no lookup
                        // is needed to invite someone later.
                        val message = String.format(
                            ScrollaStrings.GROUP_SHARE_TEMPLATE,
                            group.name,
                            group.id
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    onGroupClick = { groupId ->
                        leaderboardViewModel.selectGroup(groupId)
                        // Land on the board for the group just picked. Popping back
                        // to wherever the user came from made selection feel inert.
                        routeStack = listOf(ScreenRoute.MainTabs)
                        selectedTab = 1
                    }
                )
            }
            is ScreenRoute.JoinGroup -> {
                val groupViewModel: GroupViewModel = viewModel()
                val leaderboardViewModel: LeaderboardViewModel = viewModel()
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
                            leaderboardViewModel.refresh()
                            // Straight to the board for the group just joined.
                            routeStack = listOf(ScreenRoute.MainTabs)
                            selectedTab = 1
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
                val leaderboardViewModel: LeaderboardViewModel = viewModel()
                val isLoading by groupViewModel.isLoading.collectAsState()
                val errorMessage by groupViewModel.errorMessage.collectAsState()

                CreateGroupScreen(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onBackClick = {
                        groupViewModel.clearError()
                        popBackStack()
                    },
                    createdCode = createdGroupCode,
                    onDoneClick = {
                        createdGroupCode = null
                        groupViewModel.clearError()
                        routeStack = listOf(ScreenRoute.MainTabs)
                        selectedTab = 1
                    },
                    onCreateClick = { name ->
                        groupViewModel.createGroup(name) { code ->
                            groupViewModel.clearError()
                            // Show the code rather than popping — it is the only
                            // way anyone else can ever join this group.
                            createdGroupCode = code
                            leaderboardViewModel.refresh()
                        }
                    }
                )
            }
            is ScreenRoute.WeeklyRecap -> {
                val recapViewModel: WeeklyRecapViewModel = viewModel()
                val recapState by recapViewModel.uiState.collectAsState()

                WeeklyRecapScreen(
                    weeklyDistanceKm = recapState.weeklyDistanceKm,
                    landmarkText = recapState.landmarkText,
                    onSkipClick = popBackStack,
                    onShareClick = {
                        // S3.5's Bitmap card is not built yet; sharing the figure
                        // as text is honest and does something, where popping the
                        // back stack looked like the share had silently failed.
                        val message = String.format(
                            ScrollaStrings.RECAP_SHARE_TEMPLATE,
                            DistanceFormatter.formatDistance(recapState.weeklyDistanceKm)
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                )
            }
            is ScreenRoute.AppBreakdown -> {
                val breakdownViewModel: AppBreakdownViewModel = viewModel()
                val breakdownState by breakdownViewModel.uiState.collectAsState()

                AppBreakdownScreen(
                    hasData = breakdownState.hasData,
                    topApp = breakdownState.topApp,
                    // Needs the group leaderboard, which needs A's sync.
                    targetRank = null,
                    apps = breakdownState.apps,
                    onBackClick = popBackStack
                )
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
                0 -> {
                    val homeViewModel: HomeViewModel = viewModel()
                    val homeState by homeViewModel.uiState.collectAsState()

                    // The standing card reads the Group tab's own ViewModel rather
                    // than fetching a second copy. viewModel() here resolves to the
                    // activity's store, so this is the same instance the Leaderboard
                    // uses — one staleness window, one set of Firestore reads, and no
                    // way for the two screens to disagree about the same rank.
                    val leaderboardViewModel: LeaderboardViewModel = viewModel()
                    val boardState by leaderboardViewModel.uiState.collectAsState()
                    val standing = boardState.selfStanding()

                    RefreshOnResume { homeViewModel.refresh() }
                    LaunchedEffect(Unit) { leaderboardViewModel.refreshIfStale() }
                    RefreshOnResume { leaderboardViewModel.refreshIfStale() }

                    HomeScreen(
                        scrollDistanceKm = homeState.todayKm,
                        yesterdayKm = homeState.yesterdayKm,
                        landmarkText = homeState.landmarkText,
                        hasSensorData = homeState.hasSensorData,
                        isLoading = homeState.isLoading,
                        // Null until a rank is genuinely true — see selfStanding().
                        rankPosition = standing?.rankPosition,
                        groupSize = standing?.groupSize,
                        // Named even without a rank, so the card reads "— / College
                        // Friends" rather than disowning a group the user is in.
                        groupName = standing?.groupName ?: boardState.activeGroup?.groupName,
                        insightLabel = ScrollaStrings.HOME_INSIGHT_PEAK_HOUR_LABEL,
                        insightBody = homeState.peakHour?.let { hour ->
                            "Most of it happens between ${ScrollaFormatters.formatHourRange(hour)}."
                        },
                        onSettingsClick = onSettingsClick,
                        onRankChipClick = { onTabSelected(1) } // Switch to leaderboard
                    )
                }
                1 -> {
                    val leaderboardViewModel: LeaderboardViewModel = viewModel()
                    val boardState by leaderboardViewModel.uiState.collectAsState()

                    // Staleness check on tab open rather than a live listener (S2.4).
                    LaunchedEffect(Unit) { leaderboardViewModel.refreshIfStale() }
                    RefreshOnResume { leaderboardViewModel.refreshIfStale() }

                    LeaderboardScreen(
                        groupName = boardState.activeGroup?.groupName
                            ?: ScrollaStrings.LEADERBOARD_NO_GROUP_TITLE,
                        mostImprovedName = null,
                        entries = boardState.entries,
                        groupStats = boardState.groupStats ?: GroupStats(0f, 0f, 0f),
                        groupBestDay = boardState.groupBestDay,
                        memberCount = boardState.activeGroup?.memberCount,
                        errorMessage = boardState.errorMessage,
                        onRetryClick = { leaderboardViewModel.refresh() },
                        onSwitchGroupClick = onManageGroupsClick,
                        emptyBoardMessage = if (boardState.activeGroup == null) {
                            ScrollaStrings.LEADERBOARD_EMPTY_NO_GROUP
                        } else {
                            ScrollaStrings.LEADERBOARD_EMPTY_NO_TOTALS
                        },
                        onHallOfFameClick = onHallOfFameClick
                    )
                }
                2 -> {
                    val insightsViewModel: InsightsViewModel = viewModel()
                    val insightsState by insightsViewModel.uiState.collectAsState()

                    RefreshOnResume { insightsViewModel.refresh() }

                    InsightsScreen(
                        weekData = insightsState.weekData,
                        topApps = insightsState.topApps,
                        peakTimeText = insightsState.peakTimeText,
                        onAppBreakdownClick = onAppBreakdownClick,
                        onShowRecapClick = onShowRecapClick
                    )
                }
                3 -> {
                    val profileViewModel: ProfileViewModel = viewModel()
                    val profileState by profileViewModel.uiState.collectAsState()

                    RefreshOnResume { profileViewModel.refresh() }

                    ProfileScreen(
                        displayName = profileState.displayName,
                        memberSinceLabel = null,
                        personalBestKm = profileState.personalBestKm,
                        personalBestRelativeDate = profileState.personalBestDate,
                        sevenDayAvgKm = profileState.sevenDayAvgKm,
                        previousSevenDayAvgKm = profileState.previousSevenDayAvgKm,
                        hallOfFameGapKm = profileState.hallOfFameGapKm,
                        isRecordHolder = profileState.isRecordHolder,
                        groupCount = profileState.groupCount,
                        primaryGroupName = profileState.primaryGroupName,
                        onSettingsClick = onSettingsClick,
                        onPersonalRecordsClick = onPersonalRecordsClick,
                        onHallOfFameClick = onHallOfFameClick,
                        onManageGroupsClick = onManageGroupsClick
                    )
                }
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

