package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.AuthHelper
import com.example.data.firebase.AuthHelper.authStateFlow
import com.example.data.firebase.CommunityAndTrackingRepository
import com.example.data.local.LocalPreferencesStore
import com.example.data.sources.FootballDataRepository
import com.example.domain.model.UiState
import com.example.ui.screens.AccumulatorScreen
import com.example.ui.screens.AccuracyReportsScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminLoginScreen
import com.example.ui.screens.CommunityAndReviewsScreen
import com.example.ui.screens.CorrectScoresScreen
import com.example.ui.screens.FootballPredictionsScreen
import com.example.ui.screens.FootballStatisticsScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LiveMatchCentreScreen
import com.example.ui.screens.PredictionHistoryScreen
import com.example.ui.screens.PrivacyPolicyScreen
import com.example.ui.screens.ReadMeScreen
import com.example.ui.screens.SearchFootballScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.TeamAnalysisScreen
import com.example.ui.screens.TermsOfUseScreen
import com.example.ui.screens.UpcomingFixturesScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SilverGrey
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val FIXTURES = "fixtures"
    const val PREDICTIONS = "predictions"
    const val CORRECT_SCORES = "correct_scores"
    const val LIVE = "live"
    const val STATISTICS = "statistics"
    const val TEAM_ANALYSIS = "team_analysis"
    const val ACCUMULATOR = "accumulator"
    const val HISTORY = "history"
    const val ACCURACY = "accuracy"
    const val COMMUNITY = "community"
    const val PROFILE = "profile"
    const val README = "readme"
    const val SIGN_IN = "signin"
    const val ADMIN_LOGIN = "admin_login"
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
}

private data class NavSectionItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val primaryTabs = listOf(
    NavSectionItem(Routes.HOME, "Home", Icons.Filled.Home, "nav_home"),
    NavSectionItem(Routes.SEARCH, "Search", Icons.Filled.Search, "nav_search"),
    NavSectionItem(Routes.FIXTURES, "Fixtures", Icons.Filled.CalendarMonth, "nav_fixtures"),
    NavSectionItem(Routes.PREDICTIONS, "Predict", Icons.Filled.SportsSoccer, "nav_predictions"),
    NavSectionItem(Routes.COMMUNITY, "Community", Icons.Filled.Forum, "nav_community")
)

private val ordinaryUserSections = listOf(
    NavSectionItem(Routes.HOME, "1. Home Dashboard", Icons.Filled.Home, "drawer_home"),
    NavSectionItem(Routes.SEARCH, "2. Search Football", Icons.Filled.Search, "drawer_search"),
    NavSectionItem(Routes.FIXTURES, "3. Upcoming Fixtures", Icons.Filled.CalendarMonth, "drawer_fixtures"),
    NavSectionItem(Routes.PREDICTIONS, "4. Football Predictions", Icons.Filled.SportsSoccer, "drawer_predictions"),
    NavSectionItem(Routes.CORRECT_SCORES, "5. Correct Scores", Icons.Filled.GridView, "drawer_correct_scores"),
    NavSectionItem(Routes.LIVE, "6. Live Match Centre", Icons.Filled.LiveTv, "drawer_live"),
    NavSectionItem(Routes.STATISTICS, "7. Football Statistics", Icons.Filled.BarChart, "drawer_statistics"),
    NavSectionItem(Routes.TEAM_ANALYSIS, "8. Team Analysis", Icons.Filled.Analytics, "drawer_team_analysis"),
    NavSectionItem(Routes.ACCUMULATOR, "9. Advanced Accumulator", Icons.Filled.Layers, "drawer_accumulator"),
    NavSectionItem(Routes.HISTORY, "10. Prediction History", Icons.Filled.History, "drawer_history"),
    NavSectionItem(Routes.ACCURACY, "11. Accuracy Reports", Icons.Filled.Assessment, "drawer_accuracy"),
    NavSectionItem(Routes.COMMUNITY, "12. Community & Reviews", Icons.Filled.Forum, "drawer_community"),
    NavSectionItem(Routes.PROFILE, "13. User Profile", Icons.Filled.Person, "drawer_profile"),
    NavSectionItem(Routes.README, "14. Read Me", Icons.AutoMirrored.Filled.MenuBook, "drawer_readme"),
    NavSectionItem(Routes.SIGN_IN, "15. Sign In", Icons.AutoMirrored.Filled.Login, "drawer_signin")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val context = LocalContext.current.applicationContext
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    val uid = currentUser?.uid ?: "guest"

    val footballRepo = remember { FootballDataRepository() }
    val prefsStore = remember { LocalPreferencesStore(context) }

    key(uid) {
        val cloudRepo = remember(uid) { CommunityAndTrackingRepository(context) }
        val mainViewModel: MainViewModel = viewModel(
            key = "main_$uid",
            factory = viewModelFactory {
                initializer {
                    MainViewModel(
                        footballRepo = footballRepo,
                        cloudRepo = cloudRepo,
                        prefsStore = prefsStore,
                        currentUserId = currentUser?.uid.orEmpty()
                    )
                }
            }
        )

        val isOwner = AuthHelper.isVerifiedOwnerAdmin(currentUser)
        val adminViewModel: AdminViewModel? = if (isOwner) {
            viewModel(
                key = "admin_$uid",
                factory = viewModelFactory {
                    initializer {
                        AdminViewModel(
                            cloudRepo = cloudRepo,
                            footballRepo = footballRepo,
                            currentUserId = currentUser?.uid.orEmpty()
                        )
                    }
                }
            )
        } else null

        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        val feedState by mainViewModel.feedState.collectAsStateWithLifecycle()
        val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
        val searchBundle by mainViewModel.searchBundle.collectAsStateWithLifecycle()
        val isSearching by mainViewModel.isSearching.collectAsStateWithLifecycle()
        val searchError by mainViewModel.searchError.collectAsStateWithLifecycle()
        val recentSearches by mainViewModel.recentSearches.collectAsStateWithLifecycle()
        val selectedPrediction by mainViewModel.selectedPrediction.collectAsStateWithLifecycle()
        val selectedTeamAnalysis by mainViewModel.selectedTeamAnalysis.collectAsStateWithLifecycle()
        val isAnalyzingTeam by mainViewModel.isAnalyzingTeam.collectAsStateWithLifecycle()
        val accumulatorEval by mainViewModel.accumulatorEvaluation.collectAsStateWithLifecycle()
        val trackedPredictions by mainViewModel.trackedPredictions.collectAsStateWithLifecycle()
        val accuracySummary by mainViewModel.accuracySummary.collectAsStateWithLifecycle()
        val reviewsState by mainViewModel.reviewsState.collectAsStateWithLifecycle()
        val commentsState by mainViewModel.commentsState.collectAsStateWithLifecycle()
        val myFeedbackState by mainViewModel.myFeedbackState.collectAsStateWithLifecycle()
        val userProfile by mainViewModel.userProfile.collectAsStateWithLifecycle()
        val notificationPrefs by mainViewModel.notificationPrefs.collectAsStateWithLifecycle()
        val actionMessage by mainViewModel.actionMessage.collectAsStateWithLifecycle()

        LaunchedEffect(actionMessage) {
            val msg = actionMessage
            if (!msg.isNullOrBlank()) {
                snackbarHostState.showSnackbar(msg)
                mainViewModel.clearActionMessage()
            }
        }

        if (adminViewModel != null) {
            val adminNotice by adminViewModel.adminNotice.collectAsStateWithLifecycle()
            LaunchedEffect(adminNotice) {
                val msg = adminNotice
                if (!msg.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(msg)
                    adminViewModel.clearNotice()
                }
            }
        }

        fun navigateTo(route: String) {
            navController.navigate(route) {
                if (route in primaryTabs.map { it.route }) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    restoreState = true
                }
                launchSingleTop = true
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = CharcoalDeep,
                    drawerContentColor = IvoryWhite
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "All Football Predictions",
                                style = MaterialTheme.typography.titleLarge,
                                color = GoldBright,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                currentUser?.email ?: "Guest Session (Read-Only Feeds)",
                                style = MaterialTheme.typography.bodySmall,
                                color = SilverGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HorizontalDivider(color = CharcoalBorder)
                        Spacer(Modifier.height(6.dp))

                        ordinaryUserSections.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                selected = currentRoute == item.route,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navigateTo(item.route)
                                },
                                icon = { Icon(item.icon, contentDescription = null) },
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .testTag(item.testTag),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = GoldContainer,
                                    selectedIconColor = GoldBright,
                                    selectedTextColor = GoldBright,
                                    unselectedIconColor = SilverGrey,
                                    unselectedTextColor = IvoryWhite
                                )
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = CharcoalBorder)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "OWNER ADMINISTRATION (SEPARATE)",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        NavigationDrawerItem(
                            label = { Text("16. Administrator Login") },
                            selected = currentRoute == Routes.ADMIN_LOGIN || currentRoute == Routes.ADMIN_DASHBOARD,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navigateTo(Routes.ADMIN_LOGIN)
                            },
                            icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
                            modifier = Modifier.testTag("drawer_admin_login"),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = GoldContainer,
                                selectedIconColor = GoldBright,
                                selectedTextColor = GoldBright,
                                unselectedIconColor = GoldPrimary,
                                unselectedTextColor = IvoryWhite
                            )
                        )
                        NavigationDrawerItem(
                            label = { Text("Privacy Policy & Terms") },
                            selected = currentRoute == Routes.PRIVACY || currentRoute == Routes.TERMS,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navigateTo(Routes.PRIVACY)
                            },
                            icon = { Icon(Icons.Filled.Shield, contentDescription = null) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = GoldContainer,
                                selectedIconColor = GoldBright,
                                selectedTextColor = GoldBright,
                                unselectedIconColor = SilverGrey,
                                unselectedTextColor = SilverGrey
                            )
                        )
                    }
                }
            }
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack)
            ) {
                val isWideScreen = maxWidth >= 600.dp

                Scaffold(
                    containerColor = ObsidianBlack,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "All Football Predictions",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = GoldBright,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = "Open all navigation sections",
                                        tint = GoldBright
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { navigateTo(Routes.SEARCH) },
                                    modifier = Modifier.testTag("topbar_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = "Search Football",
                                        tint = IvoryWhite
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (currentUser != null) navigateTo(Routes.PROFILE) else navigateTo(Routes.SIGN_IN)
                                    },
                                    modifier = Modifier.testTag("topbar_account_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = if (currentUser != null) "User Profile" else "Sign In",
                                        tint = if (currentUser != null) GoldPrimary else SilverGrey
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = CharcoalDeep,
                                titleContentColor = GoldBright
                            )
                        )
                    },
                    bottomBar = {
                        if (!isWideScreen) {
                            NavigationBar(
                                containerColor = CharcoalDeep,
                                contentColor = IvoryWhite
                            ) {
                                primaryTabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = currentRoute == tab.route,
                                        onClick = { navigateTo(tab.route) },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.testTag(tab.testTag),
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = ObsidianBlack,
                                            selectedTextColor = GoldBright,
                                            indicatorColor = GoldPrimary,
                                            unselectedIconColor = SilverGrey,
                                            unselectedTextColor = SilverGrey
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isWideScreen) {
                            NavigationRail(
                                containerColor = CharcoalDeep,
                                contentColor = IvoryWhite
                            ) {
                                primaryTabs.forEach { tab ->
                                    NavigationRailItem(
                                        selected = currentRoute == tab.route,
                                        onClick = { navigateTo(tab.route) },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label, maxLines = 1) },
                                        modifier = Modifier.testTag(tab.testTag),
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = ObsidianBlack,
                                            selectedTextColor = GoldBright,
                                            indicatorColor = GoldPrimary,
                                            unselectedIconColor = SilverGrey,
                                            unselectedTextColor = SilverGrey
                                        )
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                NavigationRailItem(
                                    selected = currentRoute == Routes.README,
                                    onClick = { navigateTo(Routes.README) },
                                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Read Me") },
                                    label = { Text("Read Me") },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = ObsidianBlack,
                                        selectedTextColor = GoldBright,
                                        indicatorColor = GoldPrimary,
                                        unselectedIconColor = SilverGrey,
                                        unselectedTextColor = SilverGrey
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = Routes.HOME,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth()
                                    .widthIn(max = 920.dp)
                            ) {
                                composable(Routes.HOME) {
                                    val reviews = (reviewsState as? UiState.Success)?.data ?: emptyList()
                                    val comments = (commentsState as? UiState.Success)?.data ?: emptyList()
                                    HomeDashboardScreen(
                                        feedState = feedState,
                                        accuracySummary = accuracySummary,
                                        recentReviews = reviews,
                                        recentComments = comments,
                                        isSignedIn = currentUser != null,
                                        viewModel = mainViewModel,
                                        onOpenSearch = { navigateTo(Routes.SEARCH) },
                                        onOpenFixtures = { navigateTo(Routes.FIXTURES) },
                                        onOpenFixturePrediction = { fix ->
                                            mainViewModel.selectFixtureForPrediction(fix)
                                            navigateTo(Routes.PREDICTIONS)
                                        },
                                        onOpenTeam = { id, name, country, comp, gender, crest ->
                                            mainViewModel.openTeamAnalysisByFixtureTeam(id, name, country, comp, gender, crest)
                                            navigateTo(Routes.TEAM_ANALYSIS)
                                        },
                                        onOpenAccuracy = { navigateTo(Routes.ACCURACY) },
                                        onOpenCommunity = { navigateTo(Routes.COMMUNITY) },
                                        onOpenSignIn = { navigateTo(Routes.SIGN_IN) }
                                    )
                                }

                                composable(Routes.SEARCH) {
                                    SearchFootballScreen(
                                        query = searchQuery,
                                        suggestions = mainViewModel.searchSuggestions,
                                        recentSearches = recentSearches,
                                        bundle = searchBundle,
                                        isSearching = isSearching,
                                        error = searchError,
                                        onQueryChange = mainViewModel::onSearchQueryChanged,
                                        onSearch = mainViewModel::executeSearch,
                                        onClearSearch = mainViewModel::clearSearch,
                                        onClearRecent = mainViewModel::clearRecentSearches,
                                        onSelectTeam = { team ->
                                            mainViewModel.openTeamAnalysis(team)
                                            navigateTo(Routes.TEAM_ANALYSIS)
                                        },
                                        onSelectFixture = { fix ->
                                            mainViewModel.selectFixtureForPrediction(fix)
                                            navigateTo(Routes.PREDICTIONS)
                                        }
                                    )
                                }

                                composable(Routes.FIXTURES) {
                                    UpcomingFixturesScreen(
                                        feedState = feedState,
                                        viewModel = mainViewModel,
                                        onSelectFixture = { fix ->
                                            mainViewModel.selectFixtureForPrediction(fix)
                                            navigateTo(Routes.PREDICTIONS)
                                        },
                                        onOpenTeam = { id, name, country, comp, gender, crest ->
                                            mainViewModel.openTeamAnalysisByFixtureTeam(id, name, country, comp, gender, crest)
                                            navigateTo(Routes.TEAM_ANALYSIS)
                                        }
                                    )
                                }

                                composable(Routes.PREDICTIONS) {
                                    FootballPredictionsScreen(
                                        report = selectedPrediction,
                                        allUpcomingPredictions = feedState.allPredictionsByMatchId.values.toList(),
                                        onSelectPrediction = { fix -> mainViewModel.selectFixtureForPrediction(fix) },
                                        onSaveSnapshot = { report -> mainViewModel.trackPrediction(report) },
                                        onAddToAccumulator = { sel ->
                                            mainViewModel.toggleAccumulatorSelection(sel)
                                            navigateTo(Routes.ACCUMULATOR)
                                        },
                                        onOpenCorrectScores = { navigateTo(Routes.CORRECT_SCORES) }
                                    )
                                }

                                composable(Routes.CORRECT_SCORES) {
                                    CorrectScoresScreen(report = selectedPrediction)
                                }

                                composable(Routes.LIVE) {
                                    LiveMatchCentreScreen(
                                        feedState = feedState,
                                        onRefresh = { mainViewModel.refreshFeeds(force = true) },
                                        onSelectFixture = { fix ->
                                            mainViewModel.selectFixtureForPrediction(fix)
                                            navigateTo(Routes.PREDICTIONS)
                                        }
                                    )
                                }

                                composable(Routes.STATISTICS) {
                                    FootballStatisticsScreen(feedState = feedState)
                                }

                                composable(Routes.TEAM_ANALYSIS) {
                                    TeamAnalysisScreen(
                                        report = selectedTeamAnalysis,
                                        isLoading = isAnalyzingTeam,
                                        onSelectFixture = { fix ->
                                            mainViewModel.selectFixtureForPrediction(fix)
                                            navigateTo(Routes.PREDICTIONS)
                                        }
                                    )
                                }

                                composable(Routes.ACCUMULATOR) {
                                    AccumulatorScreen(
                                        evaluation = accumulatorEval,
                                        upcomingPredictions = feedState.allPredictionsByMatchId.values.toList(),
                                        onAddSelection = mainViewModel::toggleAccumulatorSelection,
                                        onRemoveSelection = mainViewModel::removeAccumulatorSelection,
                                        onClear = mainViewModel::clearAccumulator
                                    )
                                }

                                composable(Routes.HISTORY) {
                                    PredictionHistoryScreen(
                                        isSignedIn = currentUser != null,
                                        predictionsState = trackedPredictions,
                                        onAuthSuccess = { navigateTo(Routes.HISTORY) },
                                        onAuthError = { err ->
                                            scope.launch { snackbarHostState.showSnackbar(err) }
                                        }
                                    )
                                }

                                composable(Routes.ACCURACY) {
                                    AccuracyReportsScreen(
                                        summary = accuracySummary,
                                        isSignedIn = currentUser != null
                                    )
                                }

                                composable(Routes.COMMUNITY) {
                                    CommunityAndReviewsScreen(
                                        currentUser = currentUser,
                                        reviewsState = reviewsState,
                                        commentsState = commentsState,
                                        myFeedbackState = myFeedbackState,
                                        viewModel = mainViewModel,
                                        onAuthSuccess = { navigateTo(Routes.COMMUNITY) },
                                        onAuthError = { err ->
                                            scope.launch { snackbarHostState.showSnackbar(err) }
                                        }
                                    )
                                }

                                composable(Routes.PROFILE) {
                                    val revCount = (reviewsState as? UiState.Success)?.data?.count { it.userId == uid } ?: 0
                                    val comCount = (commentsState as? UiState.Success)?.data?.count { it.userId == uid } ?: 0
                                    val fbCount = (myFeedbackState as? UiState.Success)?.data?.size ?: 0
                                    UserProfileScreen(
                                        currentUser = currentUser,
                                        profileState = userProfile,
                                        notifPrefs = notificationPrefs,
                                        reviewCount = revCount,
                                        commentCount = comCount,
                                        feedbackCount = fbCount,
                                        viewModel = mainViewModel,
                                        onSignedOut = { navigateTo(Routes.HOME) },
                                        onOpenSignIn = { navigateTo(Routes.SIGN_IN) }
                                    )
                                }

                                composable(Routes.README) {
                                    ReadMeScreen(
                                        onOpenPrivacy = { navigateTo(Routes.PRIVACY) },
                                        onOpenTerms = { navigateTo(Routes.TERMS) }
                                    )
                                }

                                composable(Routes.SIGN_IN) {
                                    SignInScreen(
                                        currentUser = currentUser,
                                        onAuthSuccess = { navigateTo(Routes.HOME) },
                                        onOpenPrivacy = { navigateTo(Routes.PRIVACY) },
                                        onOpenTerms = { navigateTo(Routes.TERMS) },
                                        onOpenAdminLogin = { navigateTo(Routes.ADMIN_LOGIN) }
                                    )
                                }

                                composable(Routes.PRIVACY) {
                                    PrivacyPolicyScreen()
                                }

                                composable(Routes.TERMS) {
                                    TermsOfUseScreen()
                                }

                                composable(Routes.ADMIN_LOGIN) {
                                    if (adminViewModel != null) {
                                        val cfg by adminViewModel.adminConfig.collectAsStateWithLifecycle()
                                        val unlocked by adminViewModel.isPasscodeUnlocked.collectAsStateWithLifecycle()
                                        val adminErr by adminViewModel.adminAuthError.collectAsStateWithLifecycle()
                                        AdminLoginScreen(
                                            currentUser = currentUser,
                                            adminViewModel = adminViewModel,
                                            adminConfig = cfg,
                                            isPasscodeUnlocked = unlocked,
                                            authError = adminErr,
                                            onAuthSuccess = { adminViewModel.loadAdminConfig() },
                                            onOpenAdminDashboard = { navigateTo(Routes.ADMIN_DASHBOARD) }
                                        )
                                    } else {
                                        AdminLoginScreen(
                                            currentUser = currentUser,
                                            adminViewModel = null,
                                            adminConfig = null,
                                            isPasscodeUnlocked = false,
                                            authError = null,
                                            onAuthSuccess = {},
                                            onOpenAdminDashboard = {}
                                        )
                                    }
                                }

                                composable(Routes.ADMIN_DASHBOARD) {
                                    if (adminViewModel != null) {
                                        val unlocked by adminViewModel.isPasscodeUnlocked.collectAsStateWithLifecycle()
                                        val users by adminViewModel.usersState.collectAsStateWithLifecycle()
                                        val preds by adminViewModel.predictionsState.collectAsStateWithLifecycle()
                                        val revs by adminViewModel.reviewsState.collectAsStateWithLifecycle()
                                        val coms by adminViewModel.commentsState.collectAsStateWithLifecycle()
                                        val fbs by adminViewModel.feedbackState.collectAsStateWithLifecycle()
                                        val reps by adminViewModel.reportsState.collectAsStateWithLifecycle()
                                        val logs by adminViewModel.auditLogsState.collectAsStateWithLifecycle()
                                        val mods by adminViewModel.modelVersionsState.collectAsStateWithLifecycle()
                                        val adminAcc by adminViewModel.accuracySummary.collectAsStateWithLifecycle()
                                        val proposal by adminViewModel.recalibrationProposal.collectAsStateWithLifecycle()
                                        val csv by adminViewModel.exportedCsv.collectAsStateWithLifecycle()

                                        AdminDashboardScreen(
                                            currentUser = currentUser,
                                            adminViewModel = adminViewModel,
                                            sources = feedState.sourceMetadata,
                                            competitions = feedState.competitions,
                                            isPasscodeUnlocked = unlocked,
                                            usersState = users,
                                            predictionsState = preds,
                                            reviewsState = revs,
                                            commentsState = coms,
                                            feedbackState = fbs,
                                            reportsState = reps,
                                            auditLogsState = logs,
                                            modelVersionsState = mods,
                                            accuracySummary = adminAcc,
                                            recalibrationProposal = proposal,
                                            exportedCsv = csv,
                                            onBackToLogin = { navigateTo(Routes.ADMIN_LOGIN) }
                                        )
                                    } else {
                                        AdminLoginScreen(
                                            currentUser = currentUser,
                                            adminViewModel = null,
                                            adminConfig = null,
                                            isPasscodeUnlocked = false,
                                            authError = null,
                                            onAuthSuccess = {},
                                            onOpenAdminDashboard = {}
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
