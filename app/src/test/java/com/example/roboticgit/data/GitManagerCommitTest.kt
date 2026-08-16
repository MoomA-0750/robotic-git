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
 * Commit behaviour, above all the contract that a commit contains exactly what
 * the user staged. The app has a staging UI, so an implicit "add everything"
 * would silently commit work the user deliberately left out.
 */
class GitManagerCommitTest {

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

    @Test
    fun `commit includes only staged files`() = runBlocking {
        TestGitFixtures.writeFile(repoDir, "staged.txt", "please commit me\n")
        TestGitFixtures.writeFile(repoDir, "unstaged.txt", "leave me alone\n")

        val repo = TestGitFixtures.repoOf(repoDir)
        assertTrue(manager.stageFile(repo, "staged.txt").isSuccess)

        val result = manager.commit(repo, "only the staged file")
        assertTrue("commit should succeed", result.isSuccess)

        val paths = TestGitFixtures.pathsInCommit(
            git.repository,
            TestGitFixtures.headCommit(git)
        )
        assertEquals(setOf("staged.txt"), paths)
    }

    @Test
    fun `commit leaves unstaged modifications in the working tree`() = runBlocking {
        TestGitFixtures.writeFile(repoDir, "tracked.txt", "v1\n")
        val repo = TestGitFixtures.repoOf(repoDir)
        manager.stageFile(repo, "tracked.txt")
        manager.commit(repo, "add tracked.txt")

        // Modify it again but do not stage the change.
        TestGitFixtures.writeFile(repoDir, "tracked.txt", "v2\n")
        TestGitFixtures.writeFile(repoDir, "other.txt", "new file\n")
        manager.stageFile(repo, "other.txt")

        assertTrue(manager.commit(repo, "add other.txt").isSuccess)

        val paths = TestGitFixtures.pathsInCommit(
            git.repository,
            TestGitFixtures.headCommit(git)
        )
        assertEquals(setOf("other.txt"), paths)

        // The unstaged edit must survive as a working-tree change.
        val statuses = manager.getFileStatuses(repo).getOrThrow()
        assertTrue(
            "tracked.txt should still be reported as an unstaged modification, got $statuses",
            statuses.any { it.path == "tracked.txt" && !it.isStaged }
        )
    }

    @Test
    fun `commit with nothing staged fails instead of creating an empty commit`() = runBlocking {
        val repo = TestGitFixtures.repoOf(repoDir)
        val before = TestGitFixtures.headId(git)

        // An untracked file is present but was never staged.
        TestGitFixtures.writeFile(repoDir, "ignored-by-user.txt", "not staged\n")

        val result = manager.commit(repo, "should not happen")

        assertTrue("committing nothing should be reported as a failure", result.isFailure)
        assertEquals("HEAD must not move", before, TestGitFixtures.headId(git))

        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            "the message should point the user at staging, got: $message",
            message.contains("stage", ignoreCase = true)
        )
    }

    @Test
    fun `unstageFile removes a file from the next commit`() = runBlocking {
        val repo = TestGitFixtures.repoOf(repoDir)
        TestGitFixtures.writeFile(repoDir, "a.txt", "a\n")
        TestGitFixtures.writeFile(repoDir, "b.txt", "b\n")
        manager.stageFile(repo, "a.txt")
        manager.stageFile(repo, "b.txt")

        assertTrue(manager.unstageFile(repo, "b.txt").isSuccess)
        assertTrue(manager.commit(repo, "only a").isSuccess)

        val paths = TestGitFixtures.pathsInCommit(
            git.repository,
            TestGitFixtures.headCommit(git)
        )
        assertEquals(setOf("a.txt"), paths)
        assertFalse("b.txt must not be committed", "b.txt" in paths)
    }

    @Test
    fun `commit uses the supplied author identity`() = runBlocking {
        val repo = TestGitFixtures.repoOf(repoDir)
        TestGitFixtures.writeFile(repoDir, "authored.txt", "x\n")
        manager.stageFile(repo, "authored.txt")

        assertTrue(
            manager.commit(repo, "authored", "MoomA", "mooma@example.com").isSuccess
        )

        val head = TestGitFixtures.headCommit(git)
        assertEquals("MoomA", head.authorIdent.name)
        assertEquals("mooma@example.com", head.authorIdent.emailAddress)
    }

    @Test
    fun `branch name is reported from HEAD`() = runBlocking {
        assertEquals(BRANCH, manager.getCurrentBranch(TestGitFixtures.repoOf(repoDir)))
    }
}
