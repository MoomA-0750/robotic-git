package com.example.roboticgit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

    val navigationType = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> RoboticGitNavigationType.BOTTOM_NAVIGATION
        WindowWidthSizeClass.Medium -> RoboticGitNavigationType.NAVIGATION_RAIL
        WindowWidthSizeClass.Expanded -> RoboticGitNavigationType.PERMANENT_NAVIGATION_DRAWER
        else -> RoboticGitNavigationType.BOTTOM_NAVIGATION
    }

    val isDetailRoute = currentRoute?.startsWith("repo_detail") == true || 
                       currentRoute == RoboticGitDestinations.ACCOUNTS_ROUTE
    
    RoboticGitNavigationWrapper(
        navController = navController,
        navigationType = navigationType,
        currentRoute = currentRoute,
        isDetailRoute = isDetailRoute,
        navigateToHome = navActions::navigateToHome,
        navigateToSettings = navActions::navigateToSettings,
        navigateToAccounts = navActions::navigateToAccounts,
        navigateToRepoDetail = navActions::navigateToRepoDetail,
        navigateUp = navActions::navigateUp,
        settingsViewModel = settingsViewModel
    )
}

enum class RoboticGitNavigationType {
    BOTTOM_NAVIGATION, NAVIGATION_RAIL, PERMANENT_NAVIGATION_DRAWER
}

@Composable
fun RoboticGitNavigationWrapper(
    navController: NavHostController,
    navigationType: RoboticGitNavigationType,
    currentRoute: String?,
    isDetailRoute: Boolean,
    navigateToHome: () -> Unit,
    navigateToSettings: () -> Unit,
    navigateToAccounts: () -> Unit,
    navigateToRepoDetail: (String) -> Unit,
    navigateUp: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    if (navigationType == RoboticGitNavigationType.PERMANENT_NAVIGATION_DRAWER && !isDetailRoute) {
         PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet {
                     AppNavigationContent(
                        navigationType = navigationType,
                        currentRoute = currentRoute,
                        navigateToHome = navigateToHome,
                        navigateToSettings = navigateToSettings
                    )
                }
            }
        ) {
            AppContent(
                navController = navController, 
                navigateToRepoDetail = navigateToRepoDetail,
                navigateToAccounts = navigateToAccounts,
                navigateUp = navigateUp,
                settingsViewModel = settingsViewModel
            )
        }
    } else {
         Row(modifier = Modifier.fillMaxSize()) {
            if (navigationType == RoboticGitNavigationType.NAVIGATION_RAIL && !isDetailRoute) {
                AppNavigationContent(
                    navigationType = navigationType,
                    currentRoute = currentRoute,
                    navigateToHome = navigateToHome,
                    navigateToSettings = navigateToSettings
                )
            }
            
            Scaffold(
                bottomBar = {
                    if (navigationType == RoboticGitNavigationType.BOTTOM_NAVIGATION && !isDetailRoute) {
                        AppNavigationContent(
                            navigationType = navigationType,
                            currentRoute = currentRoute,
                            navigateToHome = navigateToHome,
                            navigateToSettings = navigateToSettings
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    AppContent(
                        navController = navController, 
                        navigateToRepoDetail = navigateToRepoDetail,
                        navigateToAccounts = navigateToAccounts,
                        navigateUp = navigateUp,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigationContent(
    navigationType: RoboticGitNavigationType,
    currentRoute: String?,
    navigateToHome: () -> Unit,
    navigateToSettings: () -> Unit
) {
    if (navigationType == RoboticGitNavigationType.BOTTOM_NAVIGATION) {
        NavigationBar {
            NavigationBarItem(
                selected = currentRoute == RoboticGitDestinations.HOME_ROUTE,
                onClick = navigateToHome,
                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                label = { Text("Home") }
            )
            NavigationBarItem(
                selected = currentRoute == RoboticGitDestinations.SETTINGS_ROUTE,
                onClick = navigateToSettings,
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings") }
            )
        }
    } else {
        NavigationRail {
            NavigationRailItem(
                selected = currentRoute == RoboticGitDestinations.HOME_ROUTE,
                onClick = navigateToHome,
                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                label = { Text("Home") }
            )
             NavigationRailItem(
                selected = currentRoute == RoboticGitDestinations.SETTINGS_ROUTE,
                onClick = navigateToSettings,
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings") }
            )
        }
    }
}

@Composable
fun AppContent(
    navController: NavHostController,
    navigateToRepoDetail: (String) -> Unit,
    navigateToAccounts: () -> Unit,
    navigateUp: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
     NavHost(
        navController = navController,
        startDestination = RoboticGitDestinations.HOME_ROUTE,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(RoboticGitDestinations.HOME_ROUTE) {
            HomeScreen(
                onRepoClick = navigateToRepoDetail
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
        composable(RoboticGitDestinations.REPO_DETAIL_ROUTE) { backStackEntry ->
            val repoName = backStackEntry.arguments?.getString("repoName") ?: return@composable
            RepoDetailScreen(
                repoName = repoName,
                onBack = navigateUp
            )
        }
    }
}
