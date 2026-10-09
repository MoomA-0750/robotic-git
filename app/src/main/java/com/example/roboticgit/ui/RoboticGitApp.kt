@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.roboticgit.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.roboticgit.ui.navigation.RoboticGitDestinations
import com.example.roboticgit.ui.navigation.RoboticGitNavigationActions
import com.example.roboticgit.ui.screens.AccountsScreen
import com.example.roboticgit.ui.screens.HomeScreen
import com.example.roboticgit.ui.screens.RepoDetailScreen
import com.example.roboticgit.ui.screens.SettingsScreen
import com.example.roboticgit.ui.theme.rememberScreenTransitions
import com.example.roboticgit.ui.viewmodel.SettingsViewModel

@Composable
fun RoboticGitApp(
    widthSizeClass: WindowWidthSizeClass,
    settingsViewModel: SettingsViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navActions = remember(navController) {
        RoboticGitNavigationActions(navController)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val useTwoPaneLayout = widthSizeClass != WindowWidthSizeClass.Compact
    val isDetailRoute = currentRoute?.startsWith("repo_detail") == true ||
                       currentRoute == RoboticGitDestinations.ACCOUNTS_ROUTE

    var selectedRepoName by rememberSaveable { mutableStateOf<String?>(null) }

    RoboticGitNavigationWrapper(
        navController = navController,
        currentRoute = currentRoute,
        isDetailRoute = isDetailRoute,
        useTwoPaneLayout = useTwoPaneLayout,
        selectedRepoName = selectedRepoName,
        onRepoSelected = { repoName ->
            selectedRepoName = repoName
            if (!useTwoPaneLayout) {
                navActions.navigateToRepoDetail(repoName)
            }
        },
        navigateToHome = navActions::navigateToHome,
        navigateToSettings = navActions::navigateToSettings,
        navigateToAccounts = navActions::navigateToAccounts,
        navigateToRepoDetail = navActions::navigateToRepoDetail,
        navigateUp = {
            selectedRepoName = null
            navActions.navigateUp()
        },
        settingsViewModel = settingsViewModel
    )
}

@Composable
fun RoboticGitNavigationWrapper(
    navController: NavHostController,
    currentRoute: String?,
    isDetailRoute: Boolean,
    useTwoPaneLayout: Boolean,
    selectedRepoName: String?,
    onRepoSelected: (String) -> Unit,
    navigateToHome: () -> Unit,
    navigateToSettings: () -> Unit,
    navigateToAccounts: () -> Unit,
    navigateToRepoDetail: (String) -> Unit,
    navigateUp: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!isDetailRoute) {
                FlexibleNavigationBar(
                    currentRoute = currentRoute,
                    navigateToHome = navigateToHome,
                    navigateToSettings = navigateToSettings,
                    useHorizontalLabels = useTwoPaneLayout
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AppContent(
                navController = navController,
                useTwoPaneLayout = useTwoPaneLayout,
                selectedRepoName = selectedRepoName,
                onRepoSelected = onRepoSelected,
                navigateToRepoDetail = navigateToRepoDetail,
                navigateToAccounts = navigateToAccounts,
                navigateUp = navigateUp,
                settingsViewModel = settingsViewModel
            )
        }
    }
}

@Composable
fun FlexibleNavigationBar(
    currentRoute: String?,
    navigateToHome: () -> Unit,
    navigateToSettings: () -> Unit,
    useHorizontalLabels: Boolean = false
) {
    // Tablet: items gathered in the centre with the icon beside the label.
    // Phone: items share the width with the icon above the label.
    val iconPosition =
        if (useHorizontalLabels) NavigationItemIconPosition.Start else NavigationItemIconPosition.Top
    ShortNavigationBar(
        arrangement = if (useHorizontalLabels) {
            ShortNavigationBarArrangement.Centered
        } else {
            ShortNavigationBarArrangement.EqualWeight
        }
    ) {
        ShortNavigationBarItem(
            selected = currentRoute == RoboticGitDestinations.HOME_ROUTE,
            onClick = navigateToHome,
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Home") },
            iconPosition = iconPosition
        )
        ShortNavigationBarItem(
            selected = currentRoute == RoboticGitDestinations.SETTINGS_ROUTE,
            onClick = navigateToSettings,
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
            iconPosition = iconPosition
        )
    }
}

@Composable
fun AppContent(
    navController: NavHostController,
    useTwoPaneLayout: Boolean,
    selectedRepoName: String?,
    onRepoSelected: (String) -> Unit,
    navigateToRepoDetail: (String) -> Unit,
    navigateToAccounts: () -> Unit,
    navigateUp: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    if (useTwoPaneLayout) {
        TwoPaneContent(
            navController = navController,
            selectedRepoName = selectedRepoName,
            onRepoSelected = onRepoSelected,
            navigateToAccounts = navigateToAccounts,
            navigateUp = navigateUp,
            settingsViewModel = settingsViewModel
        )
    } else {
        SinglePaneContent(
            navController = navController,
            navigateToRepoDetail = navigateToRepoDetail,
            navigateToAccounts = navigateToAccounts,
            navigateUp = navigateUp,
            settingsViewModel = settingsViewModel
        )
    }
}

@Composable
fun SinglePaneContent(
    navController: NavHostController,
    navigateToRepoDetail: (String) -> Unit,
    navigateToAccounts: () -> Unit,
    navigateUp: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val transitions = rememberScreenTransitions()
    NavHost(
        navController = navController,
        startDestination = RoboticGitDestinations.HOME_ROUTE,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { transitions.enter },
        exitTransition = { transitions.exit },
        popEnterTransition = { transitions.popEnter },
        popExitTransition = { transitions.popExit }
    ) {
        composable(
            route = RoboticGitDestinations.HOME_ROUTE,
            enterTransition = { transitions.fadeEnter },
            exitTransition = { transitions.fadeExit },
            popEnterTransition = { transitions.fadeEnter },
            popExitTransition = { transitions.fadeExit }
        ) {
            HomeScreen(onRepoClick = navigateToRepoDetail)
        }
        composable(
            route = RoboticGitDestinations.SETTINGS_ROUTE,
            enterTransition = { transitions.fadeEnter },
            exitTransition = { transitions.fadeExit },
            popEnterTransition = { transitions.fadeEnter },
            popExitTransition = { transitions.fadeExit }
        ) {
            SettingsScreen(
                onNavigateToAccounts = navigateToAccounts,
                viewModel = settingsViewModel
            )
        }
        composable(RoboticGitDestinations.ACCOUNTS_ROUTE) {
            AccountsScreen(
                onBack = navigateUp,
                viewModel = settingsViewModel
            )
        }
        composable(RoboticGitDestinations.REPO_DETAIL_ROUTE) { backStackEntry ->
            val repoName = backStackEntry.arguments?.getString("repoName") ?: return@composable
            RepoDetailScreen(
                repoName = repoName,
                onBack = navigateUp
            )
        }
    }
}

@Composable
fun TwoPaneContent(
    navController: NavHostController,
    selectedRepoName: String?,
    onRepoSelected: (String) -> Unit,
    navigateToAccounts: () -> Unit,
    navigateUp: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val transitions = rememberScreenTransitions()
    NavHost(
        navController = navController,
        startDestination = RoboticGitDestinations.HOME_ROUTE,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { transitions.fadeEnter },
        exitTransition = { transitions.fadeExit },
        popEnterTransition = { transitions.fadeEnter },
        popExitTransition = { transitions.fadeExit }
    ) {
        composable(RoboticGitDestinations.HOME_ROUTE) {
            ListDetailLayout(
                selectedRepoName = selectedRepoName,
                onRepoSelected = onRepoSelected
            )
        }
        composable(RoboticGitDestinations.SETTINGS_ROUTE) {
            SettingsScreen(
                onNavigateToAccounts = navigateToAccounts,
                viewModel = settingsViewModel
            )
        }
        composable(RoboticGitDestinations.ACCOUNTS_ROUTE) {
            AccountsScreen(
                onBack = navigateUp,
                viewModel = settingsViewModel
            )
        }
    }
}

@Composable
fun ListDetailLayout(
    selectedRepoName: String?,
    onRepoSelected: (String) -> Unit
) {
    // Draggable split ratio: fraction of total width for the list pane
    var splitFraction by remember { mutableFloatStateOf(0.35f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val listWidth = maxWidth * splitFraction

        Row(modifier = Modifier.fillMaxSize()) {
            // List Pane
            Box(
                modifier = Modifier
                    .width(listWidth)
                    .fillMaxHeight()
            ) {
                HomeScreen(
                    selectedRepoName = selectedRepoName,
                    onRepoClick = onRepoSelected
                )
            }

            // Draggable Divider
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            val newFraction = splitFraction + delta / totalWidthPx.coerceAtLeast(1f)
                            splitFraction = newFraction.coerceIn(0.02f, 0.98f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Visual grip indicator (vertical bar)
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }

            // Detail Pane
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AnimatedContent(
                    targetState = selectedRepoName,
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { it / 4 }).togetherWith(
                            fadeOut() + slideOutHorizontally { -it / 4 }
                        )
                    },
                    label = "DetailPaneTransition"
                ) { targetRepoName ->
                    if (targetRepoName != null) {
                        RepoDetailScreen(
                            repoName = targetRepoName,
                            onBack = { /* No-op in two-pane */ },
                            showBackButton = false
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Select a repository",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
