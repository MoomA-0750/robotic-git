package com.example.roboticgit.ui

import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.ui.screens.HomeScreen
import com.example.roboticgit.ui.theme.RoboticGitTheme
import com.example.roboticgit.ui.viewmodel.HomeViewModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The two ways a repository gets into the list other than the Import tab:
 * cloning into the configured directory, and opening one already on the device.
 *
 * Both were reported broken from the release build -- the clone directory
 * setting appeared to do nothing, and opening an existing repository appeared
 * not to exist as a feature at all because a rejected path said nothing.
 */
class AddRepositoryTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var workspace: File
    private lateinit var elsewhere: File

    @Before
    fun setUp() {
        workspace = OnDeviceRepositories.freshWorkspace(context)
        OnDeviceRepositories.clearTrackedRepositories(context)
        elsewhere = File(context.filesDir, "ui-test-elsewhere").also {
            it.deleteRecursively()
            it.mkdirs()
        }
    }

    @After
    fun tearDown() {
        workspace.deleteRecursively()
        elsewhere.deleteRecursively()
        OnDeviceRepositories.restoreAppState(context)
    }

    /**
     * Changing the clone directory has to affect the very next clone.
     *
     * The ViewModel used to build its [com.example.roboticgit.data.GitManager]
     * lazily and keep it, so it held the directory that was configured when the
     * home screen first appeared. Settings is a different screen that this
     * ViewModel outlives, which is why every clone kept landing in the old
     * directory until the app was restarted -- the shape of the bug is that the
     * ViewModel is *already alive* when the setting moves, so this test builds
     * it and uses it before repointing.
     */
    @Test
    fun aCloneGoesToTheDirectoryTheSettingNamesNow() {
        val repos = OnDeviceRepositories.repoWithRemote(workspace)
        val remoteUrl = repos.remoteDir.toURI().toString()
        repos.close()

        val auth = AuthManager.get(context)
        lateinit var viewModel: HomeViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = HomeViewModel(auth)
        }
        // Force the manager into existence against the original directory.
        waitFor("the workspace repository to be listed") {
            viewModel.repos.value.any { it.name == OnDeviceRepositories.REPO_NAME }
        }

        auth.setDefaultCloneDir(elsewhere.absolutePath)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.cloneRepository(remoteUrl, "moved")
        }

        waitFor("the clone to land in the newly configured directory") {
            File(elsewhere, "moved/.git").exists()
        }
        assertFalse(
            "the clone also landed in the old directory",
            File(workspace, "moved").exists()
        )
    }

    @Test
    fun aFolderThatIsNotARepositoryIsRefusedOutLoud() {
        val notARepo = File(workspace, "just-a-folder").apply { mkdirs() }
        showScreen()

        openLocalTab()
        compose.onNodeWithText("Repository folder").performTextInput(notARepo.absolutePath)
        compose.onNodeWithText("Open Repository").performClick()

        // The button used to close the dialog and do nothing at all, which is
        // indistinguishable from the feature not working.
        awaitText("No .git here")
    }

    @Test
    fun aRepositoryOutsideTheCloneDirectoryOpensByPath() {
        val outside = File(elsewhere, "brought-along")
        OnDeviceRepositories.repoWithRemote(elsewhere).close()
        File(elsewhere, OnDeviceRepositories.REPO_NAME).renameTo(outside)

        showScreen()
        openLocalTab()
        compose.onNodeWithText("Repository folder").performTextInput(outside.absolutePath)
        compose.onNodeWithText("Open Repository").performClick()

        awaitText("brought-along")
        assertTrue(
            "the path was not remembered",
            AuthManager.get(context).getTrackedRepoPaths().contains(outside.absolutePath)
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

    /** Opens the Add Repository dialog and moves to the tab for local paths. */
    private fun openLocalTab() {
        // By description, not by text: Material3 clears the semantics of an
        // extended FAB's label, so the icon's description is the button's name.
        compose.onNodeWithContentDescription("Add Repository").performClick()
        awaitText("Local")
        compose.onNodeWithText("Local").performClick()
        awaitText("Repository folder")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Polls off the compose clock, for work that finishes on a coroutine. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("timed out waiting for $what")
    }
}
