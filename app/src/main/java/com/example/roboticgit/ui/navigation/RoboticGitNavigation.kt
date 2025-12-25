package com.example.roboticgit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.example.roboticgit.ui.navigation.RoboticGitDestinations.HOME_ROUTE
import com.example.roboticgit.ui.navigation.RoboticGitDestinations.SETTINGS_ROUTE

object RoboticGitDestinations {
    const val HOME_ROUTE = "home"
    const val SETTINGS_ROUTE = "settings"
    const val REPO_DETAIL_ROUTE = "repo_detail/{repoName}"
}

class RoboticGitNavigationActions(private val navController: NavHostController) {
    fun navigateToHome() {
        navController.navigate(HOME_ROUTE) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToSettings() {
        navController.navigate(SETTINGS_ROUTE) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    
    fun navigateToRepoDetail(repoName: String) {
        navController.navigate("repo_detail/$repoName")
    }

    fun navigateUp() {
        navController.navigateUp()
    }
}
