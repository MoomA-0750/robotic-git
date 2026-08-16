package com.example.roboticgit.data

import com.example.roboticgit.data.model.FileState
import com.example.roboticgit.data.model.FileStatus
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
 * Diff generation, and the repository scan that feeds the home screen.
 *
 * The staged path deserves particular attention: it resets a tree parser from
 * an object reader and then scans with it, and the reader has to still be open
 * at the moment of the scan.
 */
class GitManagerDiffTest {

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

    private fun statusOf(path: String): FileStatus = runBlocking {
        manager.getFileStatuses(repo()).getOrThrow().single { it.path == path }
    }

    // ---- Working-tree diffs ----

    @Test
    fun `an untracked file is shown as entirely added`() = runBlocking {
        TestGitFixtures.writeFile(repoDir, "new.txt", "alpha\nbeta\n")

        val diff = manager.getFileDiff(repo(), statusOf("new.txt")).getOrThrow()

        assertTrue("expected the added lines, got:\n$diff", diff.contains("+alpha"))
        assertTrue(diff.contains("+beta"))
    }

    @Test
    fun `an unstaged modification shows both sides`() = runBlocking {
        TestGitFixtures.writeFile(repoDir, "README.md", "rewritten\n")

        val diff = manager.getFileDiff(repo(), statusOf("README.md")).getOrThrow()

        assertTrue("expected the removed line, got:\n$diff", diff.contains("-initial"))
        assertTrue("expected the added line, got:\n$diff", diff.contains("+rewritten"))
    }

    /**
     * The staged diff compares HEAD against the index, which is a different code
     * path from the working-tree comparison above and the one most likely to
     * break: it has to keep its object reader alive across the scan.
     */
    @Test
    fun `a staged modification diffs HEAD against the index`() = runBlocking {
        TestGitFixtures.writeFile(repoDir, "README.md", "staged rewrite\n")
        manager.stageFile(repo(), "README.md")

        val staged = statusOf("README.md")
        assertTrue("precondition: the row should be the staged one", staged.isStaged)

        val diff = manager.getFileDiff(repo(), staged).getOrThrow()

        assertTrue("expected the removed line, got:\n$diff", diff.contains("-initial"))
        assertTrue("expected the added line, got:\n$diff", diff.contains("+staged rewrite"))
    }

    /**
     * A nested path forces the scan to walk down into subtrees, which needs the
     * object reader after the tree parser was reset from it. Sharper than the
     * top-level case, and the one that would expose a reader closed too early.
     */
    @Test
    fun `a staged modification deep in the tree diffs correctly`() = runBlocking {
        TestGitFixtures.commitFile(
            git, repoDir, "src/main/kotlin/Deep.kt", "fun original() {}\n", "add a nested file"
        )
        TestGitFixtures.writeFile(repoDir, "src/main/kotlin/Deep.kt", "fun rewritten() {}\n")
        manager.stageFile(repo(), "src/main/kotlin/Deep.kt")

        val staged = statusOf("src/main/kotlin/Deep.kt")
        assertTrue("precondition: the row should be the staged one", staged.isStaged)

        val diff = manager.getFileDiff(repo(), staged).getOrThrow()

        assertTrue("expected the removed line, got:\n$diff", diff.contains("-fun original"))
        assertTrue("expected the added line, got:\n$diff", diff.contains("+fun rewritten"))
    }

    @Test
    fun `a staged new file diffs against nothing`() = runBlocking {
        TestGitFixtures.writeFile(repoDir, "added.txt", "brand new\n")
        manager.stageFile(repo(), "added.txt")

        val diff = manager.getFileDiff(repo(), statusOf("added.txt")).getOrThrow()

        assertTrue("expected the added line, got:\n$diff", diff.contains("+brand new"))
    }

    @Test
    fun `a file with no changes says so rather than returning an empty string`() = runBlocking {
        val untouched = FileStatus("README.md", FileState.MODIFIED, isStaged = false)

        val diff = manager.getFileDiff(repo(), untouched).getOrThrow()

        assertFalse("the UI needs something to show", diff.isBlank())
        assertTrue("got: $diff", diff.contains("No changes", ignoreCase = true))
    }

    // ---- Commit diffs ----

    @Test
    fun `a commit reports the paths it touched`() = runBlocking {
        TestGitFixtures.commitFile(git, repoDir, "one.txt", "1\n", "add one")
        TestGitFixtures.writeFile(repoDir, "two.txt", "2\n")
        TestGitFixtures.writeFile(repoDir, "README.md", "changed\n")
        git.add().addFilepattern(".").call()
        val head = git.commit().setMessage("touch two files").call()

        val changes = manager.getCommitChanges(repo(), head.name)

        assertEquals(setOf("two.txt", "README.md"), changes.map { it.path }.toSet())
        assertEquals(
            "a new file is an addition",
            "ADD",
            changes.single { it.path == "two.txt" }.changeType
        )
        assertEquals(
            "MODIFY",
            changes.single { it.path == "README.md" }.changeType
        )
    }

    @Test
    fun `the first commit reports its files rather than failing`() = runBlocking {
        val first = TestGitFixtures.headCommit(git)

        val changes = manager.getCommitChanges(repo(), first.name)

        assertEquals(listOf("README.md"), changes.map { it.path })
        assertEquals("ADD", changes.single().changeType)
    }

    @Test
    fun `a deletion is reported under its old path`() = runBlocking {
        File(repoDir, "README.md").delete()
        git.rm().addFilepattern("README.md").call()
        val head = git.commit().setMessage("remove README").call()

        val changes = manager.getCommitChanges(repo(), head.name)

        assertEquals(listOf("README.md"), changes.map { it.path })
        assertEquals("DELETE", changes.single().changeType)
    }

    @Test
    fun `a commit's diff for one file covers only that file`() = runBlocking {
        TestGitFixtures.writeFile(repoDir, "a.txt", "aaa\n")
        TestGitFixtures.writeFile(repoDir, "b.txt", "bbb\n")
        git.add().addFilepattern(".").call()
        val head = git.commit().setMessage("add two").call()

        val diff = manager.getCommitFileDiff(repo(), head.name, "a.txt")

        assertTrue("expected a.txt's content, got:\n$diff", diff.contains("+aaa"))
        assertFalse("b.txt must not leak in, got:\n$diff", diff.contains("bbb"))
    }

    @Test
    fun `an unknown commit id yields a message rather than throwing`() = runBlocking {
        val diff = manager.getCommitFileDiff(repo(), "0".repeat(40), "README.md")

        assertFalse(diff.isBlank())
        assertTrue("got: $diff", diff.contains("Error", ignoreCase = true))
    }

    // ---- Repository discovery ----

    @Test
    fun `repositories under the root directory are found`() = runBlocking {
        val second = File(rootDir, "another")
        TestGitFixtures.initRepoWithCommit(second).close()

        val repos = manager.listRepositories()

        assertEquals(listOf("another", "sample"), repos.map { it.name })
        assertTrue(
            "the last commit time should be populated",
            repos.all { it.lastCommitTime > 0L }
        )
    }

    @Test
    fun `a directory that is not a repository is ignored`() = runBlocking {
        File(rootDir, "just-a-folder").mkdirs()

        assertEquals(listOf("sample"), manager.listRepositories().map { it.name })
    }

    @Test
    fun `repositories outside the root directory are included when tracked`() = runBlocking {
        // The case the app hits when a repository is added by path -- an Obsidian
        // vault, say -- rather than cloned into the default directory.
        val outside = File(tmp.newFolder("elsewhere"), "vault")
        TestGitFixtures.initRepoWithCommit(outside).close()

        val repos = manager.listRepositories(setOf(outside.absolutePath))

        assertEquals(listOf("sample", "vault"), repos.map { it.name })
        assertEquals(
            outside.absolutePath,
            repos.single { it.name == "vault" }.localPath.absolutePath
        )
    }

    @Test
    fun `a tracked path that is also under the root is not listed twice`() = runBlocking {
        val repos = manager.listRepositories(setOf(repoDir.absolutePath))

        assertEquals(listOf("sample"), repos.map { it.name })
    }

    @Test
    fun `a tracked path that no longer exists is skipped`() = runBlocking {
        val repos = manager.listRepositories(setOf("/nowhere/at/all"))

        assertEquals(listOf("sample"), repos.map { it.name })
    }
}
