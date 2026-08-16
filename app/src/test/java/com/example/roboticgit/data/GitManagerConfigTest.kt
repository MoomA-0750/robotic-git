package com.example.roboticgit.data

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
 * Config alignment for repositories the app did not create itself.
 *
 * The real target is Android's emulated storage, which cannot store the
 * executable bit. That cannot be reproduced on a Linux build host, so these
 * tests pin down the mechanism instead: the value follows what the filesystem
 * reports, a stale value gets corrected, and repeating the call is harmless.
 */
class GitManagerConfigTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var repoDir: File
    private lateinit var git: Git
    private lateinit var manager: GitManager

    @Before
    fun setUp() {
        rootDir = tmp.newFolder("root")
        repoDir = File(rootDir, "external")
        git = TestGitFixtures.initRepoWithCommit(repoDir)
        manager = GitManager(rootDir)
    }

    @After
    fun tearDown() {
        git.close()
    }

    private fun fileModeSetting(): String? {
        val config = git.repository.config
        config.load()
        return config.getString("core", null, "fileMode")
    }

    private fun filesystemSupportsExecute(): Boolean = git.repository.fs.supportsExecute()

    @Test
    fun `stale fileMode is corrected to match the filesystem`() = runBlocking {
        // Simulate a repository carried over from a filesystem with different
        // capabilities than the one it now lives on.
        val config = git.repository.config
        config.setBoolean("core", null, "fileMode", !filesystemSupportsExecute())
        config.save()

        assertTrue(manager.alignConfigWithFilesystem(repoDir).isSuccess)

        assertEquals(
            filesystemSupportsExecute().toString(),
            fileModeSetting()
        )
    }

    @Test
    fun `alignment is idempotent`() = runBlocking {
        assertTrue(manager.alignConfigWithFilesystem(repoDir).isSuccess)
        val afterFirst = fileModeSetting()

        assertTrue(manager.alignConfigWithFilesystem(repoDir).isSuccess)
        assertEquals(afterFirst, fileModeSetting())
    }

    @Test
    fun `alignment leaves the repository usable`() = runBlocking {
        manager.alignConfigWithFilesystem(repoDir)

        val repo = TestGitFixtures.repoOf(repoDir)
        TestGitFixtures.writeFile(repoDir, "after-align.txt", "still works\n")
        assertTrue(manager.stageFile(repo, "after-align.txt").isSuccess)
        assertTrue(manager.commit(repo, "commit after aligning config").isSuccess)

        assertEquals(
            setOf("after-align.txt"),
            TestGitFixtures.pathsInCommit(git.repository, TestGitFixtures.headCommit(git))
        )
    }

    @Test
    fun `alignment on a directory that is not a repository fails cleanly`() = runBlocking {
        val notARepo = tmp.newFolder("not-a-repo")

        val result = manager.alignConfigWithFilesystem(notARepo)

        assertTrue("should report failure rather than throw", result.isFailure)
    }
}
