package com.example.roboticgit.data

import com.example.roboticgit.data.TestGitFixtures.BRANCH
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Branch listing and lifecycle, and the working-tree operations that sit next
 * to them.
 *
 * These are driven straight from the Branches tab, and a mistake here loses
 * work rather than merely displaying it wrongly: [GitManager.rollbackFile]
 * discards edits, and [GitManager.deleteBranch] can drop commits.
 */
class GitManagerBranchTest {

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
        manager = GitManager(rootDir)
    }

    @After
    fun tearDown() {
        git.close()
    }

    private fun repo() = TestGitFixtures.repoOf(repoDir)

    // ---- Listing ----

    @Test
    fun `the current branch is listed and marked`() = runBlocking {
        val branches = manager.listBranches(repo()).getOrThrow()

        val current = branches.single { it.isCurrent }
        assertEquals(BRANCH, current.name)
        assertFalse(current.isRemote)
        assertEquals("refs/heads/$BRANCH", current.fullName)
        assertEquals(TestGitFixtures.headId(git), current.lastCommitHash)
        assertEquals("initial commit", current.lastCommitMessage)
        assertTrue(
            "timestamp should be in milliseconds",
            (current.lastCommitTime ?: 0L) > 1_000_000_000_000L
        )
    }

    @Test
    fun `remote branches are listed and flagged as remote`() = runBlocking {
        val remoteDir = File(rootDir, "origin.git")
        val remote = TestGitFixtures.initBareRemote(remoteDir)
        try {
            TestGitFixtures.linkAndSeedRemote(git, remoteDir)
            git.fetch().setRemote("origin").call()

            val branches = manager.listBranches(repo()).getOrThrow()

            val tracked = branches.filter { it.isRemote }
            assertTrue("expected a remote branch, got $branches", tracked.isNotEmpty())
            assertTrue(tracked.all { !it.isCurrent })
            assertTrue(
                "remote names drop the refs/remotes/ prefix, got ${tracked.map { it.name }}",
                tracked.any { it.name == "origin/$BRANCH" }
            )
        } finally {
            remote.close()
        }
    }

    // ---- Lifecycle ----

    @Test
    fun `a created branch appears without becoming current`() = runBlocking {
        assertTrue(manager.createBranch(repo(), "feature").isSuccess)

        val branches = manager.listBranches(repo()).getOrThrow()
        val feature = branches.single { it.name == "feature" }
        assertFalse("creating a branch should not switch to it", feature.isCurrent)
        assertEquals(BRANCH, manager.getCurrentBranch(repo()))
    }

    @Test
    fun `creating a branch that already exists fails`() = runBlocking {
        manager.createBranch(repo(), "feature")

        val second = manager.createBranch(repo(), "feature")

        assertTrue("a duplicate name must not silently succeed", second.isFailure)
    }

    @Test
    fun `a branch can be created at an earlier commit`() = runBlocking {
        val firstCommit = TestGitFixtures.headId(git)
        TestGitFixtures.commitFile(git, repoDir, "later.txt", "later\n", "a later commit")

        assertTrue(manager.createBranch(repo(), "from-start", startPoint = firstCommit).isSuccess)

        val branch = manager.listBranches(repo()).getOrThrow().single { it.name == "from-start" }
        assertEquals(firstCommit, branch.lastCommitHash)
    }

    @Test
    fun `checkout switches the branch and the working tree`() = runBlocking {
        TestGitFixtures.branchWithCommit(git, repoDir, "feature", "only-on-feature.txt", "hi\n")

        assertTrue(manager.checkoutBranch(repo(), "feature").isSuccess)

        assertEquals("feature", manager.getCurrentBranch(repo()))
        assertTrue(File(repoDir, "only-on-feature.txt").exists())

        assertTrue(manager.checkoutBranch(repo(), BRANCH).isSuccess)
        assertEquals(BRANCH, manager.getCurrentBranch(repo()))
        assertFalse(
            "the file belongs to the other branch",
            File(repoDir, "only-on-feature.txt").exists()
        )
    }

    @Test
    fun `checking out a branch that does not exist fails`() = runBlocking {
        val result = manager.checkoutBranch(repo(), "no-such-branch")

        assertTrue(result.isFailure)
        assertEquals("still on the original branch", BRANCH, manager.getCurrentBranch(repo()))
    }

    @Test
    fun `a merged branch can be deleted`() = runBlocking {
        manager.createBranch(repo(), "feature")

        assertTrue(manager.deleteBranch(repo(), "feature").isSuccess)

        assertTrue(manager.listBranches(repo()).getOrThrow().none { it.name == "feature" })
    }

    /**
     * Without force, git refuses to delete a branch holding commits that are not
     * reachable from anywhere else. That refusal is the only thing standing
     * between the Branches tab and silent data loss.
     */
    @Test
    fun `deleting an unmerged branch is refused unless forced`() = runBlocking {
        TestGitFixtures.branchWithCommit(git, repoDir, "feature", "unmerged.txt", "work\n")

        val refused = manager.deleteBranch(repo(), "feature")
        assertTrue("unmerged work must not be dropped by default", refused.isFailure)
        assertTrue(manager.listBranches(repo()).getOrThrow().any { it.name == "feature" })

        val forced = manager.deleteBranch(repo(), "feature", force = true)
        assertTrue("force should go through", forced.isSuccess)
        assertTrue(manager.listBranches(repo()).getOrThrow().none { it.name == "feature" })
    }

    @Test
    fun `the checked-out branch cannot be deleted`() = runBlocking {
        val result = manager.deleteBranch(repo(), BRANCH)

        assertTrue(result.isFailure)
        assertEquals(BRANCH, manager.getCurrentBranch(repo()))
    }

    // ---- Working tree ----

    @Test
    fun `rollback restores a tracked file to its committed content`() = runBlocking {
        TestGitFixtures.writeFile(repoDir, "README.md", "edited by mistake\n")

        assertTrue(manager.rollbackFile(repo(), "README.md").isSuccess)

        assertEquals("initial\n", TestGitFixtures.readFile(repoDir, "README.md"))
        assertTrue(manager.getFileStatuses(repo()).getOrThrow().isEmpty())
    }

    @Test
    fun `rollback leaves other files alone`() = runBlocking {
        TestGitFixtures.commitFile(git, repoDir, "keep.txt", "committed\n", "add keep.txt")
        TestGitFixtures.writeFile(repoDir, "README.md", "edited\n")
        TestGitFixtures.writeFile(repoDir, "keep.txt", "also edited\n")

        manager.rollbackFile(repo(), "README.md")

        assertEquals("initial\n", TestGitFixtures.readFile(repoDir, "README.md"))
        assertEquals(
            "the other edit must survive",
            "also edited\n",
            TestGitFixtures.readFile(repoDir, "keep.txt")
        )
    }

    @Test
    fun `rollback does not remove an untracked file`() = runBlocking {
        // checkout has nothing to restore for a path git does not track; the file
        // must not be deleted as a side effect.
        TestGitFixtures.writeFile(repoDir, "scratch.txt", "not tracked\n")

        manager.rollbackFile(repo(), "scratch.txt")

        assertTrue("an untracked file is not git's to delete", File(repoDir, "scratch.txt").exists())
    }
}
