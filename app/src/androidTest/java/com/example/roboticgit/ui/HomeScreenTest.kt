package com.example.roboticgit.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roboticgit.ui.screens.HomeScreen
import com.example.roboticgit.ui.theme.RoboticGitTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The repository list screen, in the state that is easiest to get wrong: empty.
 */
class HomeScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var workspace: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        workspace = OnDeviceRepositories.freshWorkspace(context)
        // Repositories added by path live outside the clone directory and would
        // otherwise still be listed, so the empty state would never be reached.
        OnDeviceRepositories.clearTrackedRepositories(context)
    }

    @After
    fun tearDown() {
        workspace.deleteRecursively()
        OnDeviceRepositories.restoreAppState(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
    }

    private fun showScreen() {
        compose.setContent {
            RoboticGitTheme(dynamicColor = false) {
                HomeScreen(onRepoClick = {})
            }
        }
        compose.waitForIdle()
    }

    /**
     * The title used to be the first item of the repository list, so a user with
     * nothing cloned yet met an untitled screen. It has to stand on its own.
     */
    @Test
    fun theScreenIsTitledEvenWithNoRepositories() {
        showScreen()

        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("No repositories found")).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Repositories").assertIsDisplayed()
    }

    @Test
    fun theScreenIsStillTitledOnceARepositoryExists() {
        OnDeviceRepositories.repoWithRemote(workspace).close()
        showScreen()

        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(OnDeviceRepositories.REPO_NAME)).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Repositories").assertIsDisplayed()
    }
}
