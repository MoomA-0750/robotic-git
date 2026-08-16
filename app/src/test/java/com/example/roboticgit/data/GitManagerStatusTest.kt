package com.example.roboticgit.data

import com.example.roboticgit.data.model.FileState
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * What the Changes tab shows. These tests pin down the mapping from JGit's
 * [org.eclipse.jgit.api.Status] buckets onto the flat list the UI renders,
 * because that mapping is where "the display diverges from reality" would
 * first show up.
 */
class GitManagerStatusTest {

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

    private fun statuses() = runBlocking {
        manager.getFileStatuses(TestGitFixtures.repoOf(repoDir)).getOrThrow()
    }

    @Test
    fun `untracked file is listed once as unstaged`() {
        TestGitFixtures.writeFile(repoDir, "new.txt", "hi\n")

        val result = statuses().filter { it.path == "new.txt" }

        assertEquals("expected exactly one row, got $result", 1, result.size)
        assertEquals(FileState.UNTRACKED, result.single().state)
        assertEquals(false, result.single().isStaged)
    }

    @Test
    fun `staged new file is listed once as added and staged`() = runBlocking {
        TestGitFixtures.writeFile(repoDir, "new.txt", "hi\n")
        manager.stageFile(TestGitFixtures.repoOf(repoDir), "new.txt")

        val result = statuses().filter { it.path == "new.txt" }

        assertEquals("expected exactly one row, got $result", 1, result.size)
        assertEquals(FileState.ADDED, result.single().state)
        assertEquals(true, result.single().isStaged)
    }

    @Test
    fun `modified tracked file is listed once as unstaged`() {
        TestGitFixtures.writeFile(repoDir, "README.md", "changed\n")

        val result = statuses().filter { it.path == "README.md" }

        assertEquals("expected exactly one row, got $result", 1, result.size)
        assertEquals(FileState.MODIFIED, result.single().state)
        assertEquals(false, result.single().isStaged)
    }

    @Test
    fun `staged modification is listed once as staged`() = runBlocking {
        TestGitFixtures.writeFile(repoDir, "README.md", "changed\n")
        manager.stageFile(TestGitFixtures.repoOf(repoDir), "README.md")

        val result = statuses().filter { it.path == "README.md" }

        assertEquals("expected exactly one row, got $result", 1, result.size)
        assertEquals(FileState.MODIFIED, result.single().state)
        assertEquals(true, result.single().isStaged)
    }

    /**
     * Staging a change and then editing the file again genuinely puts it in two
     * of git's buckets at once; `git status` itself lists it under both
     * "to be committed" and "not staged". Two rows is therefore correct, and
     * this test exists so that a future de-duplication pass does not "fix" it.
     */
    @Test
    fun `file staged and then edited again is listed as both staged and unstaged`() = runBlocking {
        TestGitFixtures.writeFile(repoDir, "README.md", "staged version\n")
        manager.stageFile(TestGitFixtures.repoOf(repoDir), "README.md")
        TestGitFixtures.writeFile(repoDir, "README.md", "working tree version\n")

        val result = statuses().filter { it.path == "README.md" }

        assertEquals("expected a staged row and an unstaged row, got $result", 2, result.size)
        assertEquals(1, result.count { it.isStaged })
        assertEquals(1, result.count { !it.isStaged })
    }

    @Test
    fun `deleted tracked file is listed as missing`() {
        File(repoDir, "README.md").delete()

        val result = statuses().filter { it.path == "README.md" }

        assertEquals("expected exactly one row, got $result", 1, result.size)
        assertEquals(FileState.MISSING, result.single().state)
    }

    @Test
    fun `clean repository reports no changes`() {
        assertTrue("a freshly committed repo should be clean", statuses().isEmpty())
    }

    @Test
    fun `hasUncommittedChanges reflects the working tree`() = runBlocking {
        val repo = TestGitFixtures.repoOf(repoDir)
        assertEquals(false, manager.hasUncommittedChanges(repo))

        TestGitFixtures.writeFile(repoDir, "dirty.txt", "x\n")
        assertEquals(true, manager.hasUncommittedChanges(repo))
    }

    @Test
    fun `snapshot caps the history and says when it was truncated`() = runBlocking {
        val repo = TestGitFixtures.repoOf(repoDir)
        repeat(10) { i ->
            TestGitFixtures.writeFile(repoDir, "f$i.txt", "$i\n")
            manager.stageFile(repo, "f$i.txt")
            manager.commit(repo, "commit $i")
        }
        // 11 commits exist in total, counting the fixture's initial commit.

        val limited = manager.loadSnapshot(repo, commitLimit = 5).getOrThrow()
        assertEquals(5, limited.commits.size)
        assertEquals(true, limited.hasMoreCommits)

        val complete = manager.loadSnapshot(repo, commitLimit = 50).getOrThrow()
        assertEquals(11, complete.commits.size)
        assertEquals(false, complete.hasMoreCommits)
    }

    @Test
    fun `snapshot carries everything the detail screen needs`() = runBlocking {
        val repo = TestGitFixtures.repoOf(repoDir)
        TestGitFixtures.writeFile(repoDir, "pending.txt", "x\n")

        val snapshot = manager.loadSnapshot(repo, commitLimit = 10).getOrThrow()

        assertEquals(TestGitFixtures.BRANCH, snapshot.currentBranch)
        assertEquals(1, snapshot.commits.size)
        assertEquals(listOf("pending.txt"), snapshot.fileStatuses.map { it.path })
        assertTrue("the local branch should be listed", snapshot.branches.any { it.isCurrent })
        assertEquals(false, snapshot.isMerging)
        assertTrue(snapshot.conflictingFiles.isEmpty())
    }

    @Test
    fun `commit summaries carry the fields the UI renders`() = runBlocking {
        val repo = TestGitFixtures.repoOf(repoDir)
        TestGitFixtures.writeFile(repoDir, "summary.txt", "x\n")
        manager.stageFile(repo, "summary.txt")
        manager.commit(repo, "subject line\n\nbody paragraph\n", "MoomA", "mooma@example.com")

        val head = manager.loadSnapshot(repo, commitLimit = 1).getOrThrow().commits.single()

        assertEquals("subject line", head.shortMessage)
        assertEquals("MoomA", head.authorName)
        assertEquals("mooma@example.com", head.authorEmail)
        assertEquals(40, head.id.length)
        assertEquals(head.id.take(7), head.abbreviatedId)
        assertTrue("a commit with a body should report one", head.hasBody)
        assertTrue("timestamp should be in milliseconds", head.timestamp > 1_000_000_000_000L)
    }

    @Test
    fun `snapshot on a directory that is not a repository reports a typed error`() = runBlocking {
        val notARepo = tmp.newFolder("empty")

        val result = manager.loadSnapshot(TestGitFixtures.repoOf(notARepo), commitLimit = 10)

        assertTrue(result.isFailure)
        assertTrue(
            "expected a GitError, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is GitError
        )
    }
}
