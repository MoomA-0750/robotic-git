package com.example.roboticgit.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.roboticgit.ui.screens.stageCheckboxTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roboticgit.ui.screens.RepoDetailScreen
import com.example.roboticgit.ui.theme.RoboticGitTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * What the repository screen actually shows.
 *
 * The data layer is covered by JVM tests; these exist for the half that was
 * only ever checked by looking at it. Above all the reporting of push results,
 * which is where the original complaint came from: a rejected push was
 * announced as "Push successful", inside a dialog titled "Error".
 *
 * Real repositories are used, with a bare repository standing in for the remote
 * over JGit's local transport, so push genuinely succeeds or is genuinely
 * rejected without a network or a token.
 */
class RepoDetailScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var workspace: File
    private lateinit var repos: OnDeviceRepositories.Repos

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        workspace = OnDeviceRepositories.freshWorkspace(context)
        repos = OnDeviceRepositories.repoWithRemote(workspace)
    }

    @After
    fun tearDown() {
        repos.close()
        workspace.deleteRecursively()
    }

    private fun showScreen() {
        compose.setContent {
            RoboticGitTheme(dynamicColor = false) {
                RepoDetailScreen(
                    repoName = OnDeviceRepositories.REPO_NAME,
                    onBack = {}
                )
            }
        }
        compose.waitForIdle()
    }

    /** Waits for [text] to appear, so the assertion does not race the coroutine. */
    private fun awaitText(text: String, substring: Boolean = false, timeoutMs: Long = 15_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodes(hasText(text, substring = substring))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun textExists(text: String, substring: Boolean = false): Boolean =
        compose.onAllNodes(hasText(text, substring = substring))
            .fetchSemanticsNodes().isNotEmpty()

    // ---- Push reporting: the original defect ----

    /**
     * The regression test for the bug this whole effort started from. A push
     * with nothing to send must not be announced through the error channel.
     */
    @Test
    fun pushWithNothingToSendIsNotReportedAsAnError() {
        showScreen()
        awaitText(OnDeviceRepositories.REPO_NAME)

        compose.onNodeWithText("Push").performClick()

        awaitText("Everything up to date")
        assertTrue(
            "a push that changed nothing must not raise the Error dialog",
            !textExists("Error")
        )
    }

    @Test
    fun aRejectedPushIsReportedAsAFailureAndSaysWhy() {
        OnDeviceRepositories.makeRemoteDiverge(repos, workspace)
        val remoteBefore = repos.remote.repository.resolve("refs/heads/${OnDeviceRepositories.BRANCH}").name
        showScreen()
        awaitText(OnDeviceRepositories.REPO_NAME)

        compose.onNodeWithText("Push").performClick()

        // The dialog is the error channel; a rejection belongs in it.
        awaitText("Error")
        compose.onNodeWithText("rejected", substring = true).assertIsDisplayed()
        assertTrue(
            "the wording should not claim success",
            !textExists("Push successful", substring = true)
        )
        assertEquals(
            "nothing was transferred",
            remoteBefore,
            repos.remote.repository.resolve("refs/heads/${OnDeviceRepositories.BRANCH}").name
        )
    }

    @Test
    fun aPushThatSendsSomethingSaysWhatItSent() {
        OnDeviceRepositories.writeFile(repos.localDir, "outgoing.txt", "to publish\n")
        repos.local.add().addFilepattern("outgoing.txt").call()
        repos.local.commit().setMessage("something to push").call()
        val expected = OnDeviceRepositories.headId(repos.local)

        showScreen()
        awaitText(OnDeviceRepositories.REPO_NAME)

        compose.onNodeWithText("Push").performClick()

        awaitText("Pushed", substring = true)
        assertTrue("success is not an error", !textExists("Error"))
        assertEquals(
            "the remote should have advanced",
            expected,
            repos.remote.repository.resolve("refs/heads/${OnDeviceRepositories.BRANCH}").name
        )
    }

    // ---- Committing ----

    @Test
    fun theCommitButtonDoesNothingWhileNothingIsStaged() {
        OnDeviceRepositories.writeFile(repos.localDir, "unstaged.txt", "not chosen\n")
        val headBefore = OnDeviceRepositories.headId(repos.local)

        showScreen()
        awaitText("unstaged.txt")

        // A message alone must not be enough: the button stays inert until
        // something is actually staged.
        compose.onNodeWithText("Commit message").performTextInput("should not commit")
        compose.onNodeWithContentDescription("Commit").performClick()
        compose.waitForIdle()

        assertEquals(
            "HEAD must not move",
            headBefore,
            OnDeviceRepositories.headId(repos.local)
        )
        assertTrue("the file is still waiting", textExists("unstaged.txt"))
    }

    @Test
    fun committingIncludesOnlyTheStagedFile() {
        OnDeviceRepositories.writeFile(repos.localDir, "chosen.txt", "commit me\n")
        OnDeviceRepositories.writeFile(repos.localDir, "ignored.txt", "leave me\n")

        showScreen()
        awaitText("chosen.txt")

        // Addressed by tag: the row's text is merged with its state label, so it
        // matches more than one node and cannot be clicked unambiguously.
        compose.onNodeWithTag(stageCheckboxTag("chosen.txt")).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Commit message").performTextInput("only the chosen one")
        compose.onNodeWithContentDescription("Commit").performClick()

        compose.waitUntil(15_000) {
            OnDeviceRepositories.pathsInHeadCommit(repos.local).contains("chosen.txt")
        }

        assertEquals(
            "the unstaged file must stay out of the commit",
            setOf("chosen.txt"),
            OnDeviceRepositories.pathsInHeadCommit(repos.local)
        )
    }

    // ---- Loading behaviour ----

    @Test
    fun theRepositoryNameStaysVisibleWhileRefreshing() {
        OnDeviceRepositories.writeFile(repos.localDir, "a.txt", "a\n")
        showScreen()
        awaitText("a.txt")

        compose.onNodeWithContentDescription("Refresh").performClick()

        // A refresh over existing content must not swap the screen for a spinner.
        compose.onNodeWithText(OnDeviceRepositories.REPO_NAME).assertIsDisplayed()
        compose.waitForIdle()
        assertTrue("the file list survives the refresh", textExists("a.txt"))
    }
}
