package com.example.roboticgit.data

import com.example.roboticgit.data.TestGitFixtures.BRANCH
import com.example.roboticgit.data.model.FileState
import com.example.roboticgit.data.model.MergeStatus
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Merging, and the conflict state a merge can leave behind.
 *
 * This is the largest part of the data layer that had no coverage at all: five
 * distinct merge outcomes are mapped onto [MergeStatus], and the conflict path
 * spans four more methods that the UI drives in sequence. All of it runs
 * locally, so none of it needs a network or a device.
 */
class GitManagerMergeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var repoDir: File
    private lateinit var git: Git
    private lateinit var manager: GitManager

    @Before
    fun setUp() {
        rootDir = tmp.newFolder("root")
        repoDir = File(rootDir, "sample")
        git = TestGitFixtures.initRepoWithCommit(repoDir)
        TestGitFixtures.commitFile(git, repoDir, "shared.txt", "base\n", "add shared.txt")
        manager = GitManager(rootDir)
    }

    @After
    fun tearDown() {
        git.close()
    }

    private fun repo() = TestGitFixtures.repoOf(repoDir)

    /** Puts the repository into a conflicted merge and returns the merge result. */
    private fun startConflictingMerge() = runBlocking {
        TestGitFixtures.branchWithCommit(git, repoDir, "feature", "shared.txt", "feature version\n")
        TestGitFixtures.commitFile(git, repoDir, "shared.txt", "main version\n", "main edits shared")
        manager.mergeBranch(repo(), "feature")
    }

    // ---- Merge outcomes ----

    @Test
    fun `merging a branch ahead of this one fast-forwards`() = runBlocking {
        TestGitFixtures.branchWithCommit(git, repoDir, "feature", "feature.txt", "hi\n")

        val result = manager.mergeBranch(repo(), "feature")

        assertEquals(MergeStatus.FAST_FORWARD, result.status)
        assertNotNull("the resulting commit should be reported", result.mergedCommitHash)
        assertEquals("hi\n", TestGitFixtures.readFile(repoDir, "feature.txt"))
    }

    @Test
    fun `merging diverged branches that do not overlap succeeds`() = runBlocking {
        TestGitFixtures.branchWithCommit(git, repoDir, "feature", "feature.txt", "from feature\n")
        TestGitFixtures.commitFile(git, repoDir, "main.txt", "from main\n", "main adds a file")

        val result = manager.mergeBranch(repo(), "feature")

        assertEquals(MergeStatus.SUCCESS, result.status)
        assertNotNull(result.mergedCommitHash)
        // Both sides' work is present.
        assertEquals("from feature\n", TestGitFixtures.readFile(repoDir, "feature.txt"))
        assertEquals("from main\n", TestGitFixtures.readFile(repoDir, "main.txt"))
        assertFalse("a completed merge leaves no merge state", manager.isMerging(repo()))
    }

    @Test
    fun `merging a branch that is already contained reports up to date`() = runBlocking {
        TestGitFixtures.branchWithCommit(git, repoDir, "feature", "feature.txt", "hi\n")
        manager.mergeBranch(repo(), "feature")

        val second = manager.mergeBranch(repo(), "feature")

        assertEquals(MergeStatus.ALREADY_UP_TO_DATE, second.status)
    }

    @Test
    fun `fast-forward-only refuses a merge that would need a commit`() = runBlocking {
        TestGitFixtures.branchWithCommit(git, repoDir, "feature", "feature.txt", "from feature\n")
        TestGitFixtures.commitFile(git, repoDir, "main.txt", "from main\n", "main adds a file")
        val headBefore = TestGitFixtures.headId(git)

        val result = manager.mergeBranch(repo(), "feature", fastForwardOnly = true)

        assertEquals(MergeStatus.FAILED, result.status)
        assertEquals("HEAD must not move", headBefore, TestGitFixtures.headId(git))
    }

    @Test
    fun `merging a branch that does not exist names it`() = runBlocking {
        val result = manager.mergeBranch(repo(), "no-such-branch")

        assertEquals(MergeStatus.FAILED, result.status)
        assertTrue(
            "the message should name the branch, got: ${result.message}",
            result.message.contains("no-such-branch")
        )
    }

    @Test
    fun `a merge commit can carry a supplied message`() = runBlocking {
        TestGitFixtures.branchWithCommit(git, repoDir, "feature", "feature.txt", "from feature\n")
        TestGitFixtures.commitFile(git, repoDir, "main.txt", "from main\n", "main adds a file")

        manager.mergeBranch(repo(), "feature", commitMessage = "merge feature into main")

        assertEquals("merge feature into main", TestGitFixtures.headCommit(git).fullMessage.trim())
    }

    // ---- Conflicts ----

    @Test
    fun `a conflicting merge is reported with the offending paths`() {
        val result = startConflictingMerge()

        assertEquals(MergeStatus.CONFLICTING, result.status)
        assertEquals(listOf("shared.txt"), result.conflictingFiles)
    }

    @Test
    fun `a conflicting merge leaves the repository in a merging state`() = runBlocking {
        startConflictingMerge()

        assertTrue("isMerging should report the interrupted merge", manager.isMerging(repo()))
        assertEquals(listOf("shared.txt"), manager.getConflictingFiles(repo()))
    }

    @Test
    fun `a conflicted file is listed as conflicting in the changes view`() = runBlocking {
        startConflictingMerge()

        val statuses = manager.getFileStatuses(repo()).getOrThrow()
        val shared = statuses.filter { it.path == "shared.txt" }

        assertEquals("expected exactly one row, got $shared", 1, shared.size)
        assertEquals(FileState.CONFLICTING, shared.single().state)
    }

    @Test
    fun `conflict content separates our version from theirs`() = runBlocking {
        startConflictingMerge()

        val conflict = manager.getConflictContent(repo(), "shared.txt")

        assertNotNull("conflict content should be readable", conflict)
        requireNotNull(conflict)
        assertEquals("shared.txt", conflict.path)
        assertTrue(
            "ours should hold this branch's line, got: ${conflict.oursContent}",
            conflict.oursContent.contains("main version")
        )
        assertFalse(
            "ours must not hold the incoming line, got: ${conflict.oursContent}",
            conflict.oursContent.contains("feature version")
        )
        assertTrue(
            "theirs should hold the incoming line, got: ${conflict.theirsContent}",
            conflict.theirsContent.contains("feature version")
        )
        assertFalse(
            "theirs must not hold this branch's line, got: ${conflict.theirsContent}",
            conflict.theirsContent.contains("main version")
        )
    }

    @Test
    fun `conflict markers are not left in either extracted version`() = runBlocking {
        startConflictingMerge()

        val conflict = requireNotNull(manager.getConflictContent(repo(), "shared.txt"))

        listOf(conflict.oursContent, conflict.theirsContent).forEach { text ->
            assertFalse("markers should be stripped, got: $text", text.contains("<<<<<<<"))
            assertFalse("markers should be stripped, got: $text", text.contains("======="))
            assertFalse("markers should be stripped, got: $text", text.contains(">>>>>>>"))
        }
    }

    @Test
    fun `the conflict region records where the markers were`() = runBlocking {
        startConflictingMerge()

        val conflict = requireNotNull(manager.getConflictContent(repo(), "shared.txt"))

        assertEquals("one conflicting hunk", 1, conflict.conflictMarkers.size)
        val region = conflict.conflictMarkers.single()
        assertTrue("the region should span at least the markers", region.endLine > region.startLine)
        assertTrue(region.oursLines.any { it.contains("main version") })
        assertTrue(region.theirsLines.any { it.contains("feature version") })
    }

    @Test
    fun `completing a merge is refused while a conflict remains`() = runBlocking {
        startConflictingMerge()

        val result = manager.completeMerge(repo())

        assertTrue("an unresolved merge must not be committed", result.isFailure)
        assertTrue("still merging", manager.isMerging(repo()))
    }

    @Test
    fun `resolving a conflict stages the resolution and clears it`() = runBlocking {
        startConflictingMerge()

        val resolved = manager.resolveConflict(repo(), "shared.txt", "reconciled\n")

        assertTrue(resolved.isSuccess)
        assertEquals("reconciled\n", TestGitFixtures.readFile(repoDir, "shared.txt"))
        assertTrue(
            "the path should no longer be conflicting",
            manager.getConflictingFiles(repo()).isEmpty()
        )
    }

    @Test
    fun `a resolved merge can be completed and ends the merging state`() = runBlocking {
        startConflictingMerge()
        manager.resolveConflict(repo(), "shared.txt", "reconciled\n")

        val completed = manager.completeMerge(repo(), "resolve shared.txt")

        assertTrue("completing should succeed, got ${completed.exceptionOrNull()}", completed.isSuccess)
        assertFalse("the merge is over", manager.isMerging(repo()))
        assertEquals("resolve shared.txt", TestGitFixtures.headCommit(git).fullMessage.trim())
        assertEquals("reconciled\n", TestGitFixtures.readFile(repoDir, "shared.txt"))
    }

    @Test
    fun `the completed merge commit has both branches as parents`() = runBlocking {
        startConflictingMerge()
        manager.resolveConflict(repo(), "shared.txt", "reconciled\n")
        manager.completeMerge(repo())

        assertEquals(
            "a merge commit records where it came from",
            2,
            TestGitFixtures.headCommit(git).parentCount
        )
    }

    @Test
    fun `aborting a conflicted merge restores a clean working tree`() = runBlocking {
        startConflictingMerge()
        // Read after the setup commits: a conflicted merge has not committed
        // anything yet, so this is where HEAD must still be once the merge is
        // abandoned.
        val headDuringMerge = TestGitFixtures.headId(git)

        val aborted = manager.abortMerge(repo())

        assertTrue("abort should succeed, got ${aborted.exceptionOrNull()}", aborted.isSuccess)
        assertEquals("HEAD stays on this branch's commit", headDuringMerge, TestGitFixtures.headId(git))
        assertEquals(
            "the file goes back to this branch's version",
            "main version\n",
            TestGitFixtures.readFile(repoDir, "shared.txt")
        )
        assertTrue("nothing left conflicting", manager.getConflictingFiles(repo()).isEmpty())
        assertFalse("the merge state is gone", manager.isMerging(repo()))
    }

    @Test
    fun `merging is possible again after an abort`() = runBlocking {
        startConflictingMerge()
        manager.abortMerge(repo())

        val second = manager.mergeBranch(repo(), "feature")

        assertEquals(
            "the same merge should be attemptable again",
            MergeStatus.CONFLICTING,
            second.status
        )
    }
}
