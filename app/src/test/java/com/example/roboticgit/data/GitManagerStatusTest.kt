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
    fun `getCommits respects the requested limit`() = runBlocking {
        val repo = TestGitFixtures.repoOf(repoDir)
        repeat(10) { i ->
            TestGitFixtures.writeFile(repoDir, "f$i.txt", "$i\n")
            manager.stageFile(repo, "f$i.txt")
            manager.commit(repo, "commit $i")
        }

        val limited = manager.getCommits(repo, limit = 5).getOrThrow()
        assertEquals(5, limited.size)

        val all = manager.getCommits(repo).getOrThrow()
        assertEquals("11 commits exist in total", 11, all.size)
    }
}
