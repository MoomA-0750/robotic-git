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

    /**
     * Probed independently of the production code, so the test asserts against
     * the filesystem rather than against the implementation's own answer.
     */
    private fun filesystemStoresExecutableBit(): Boolean {
        val probe = tmp.newFile("probe-${probeCounter++}")
        return probe.setExecutable(true, true) && probe.canExecute()
    }

    private var probeCounter = 0

    @Test
    fun `stale fileMode is corrected to match the filesystem`() = runBlocking {
        // Simulate a repository carried over from a filesystem with different
        // capabilities than the one it now lives on.
        val config = git.repository.config
        config.setBoolean("core", null, "fileMode", !filesystemStoresExecutableBit())
        config.save()

        assertTrue(manager.alignConfigWithFilesystem(repoDir).isSuccess)

        assertEquals(
            filesystemStoresExecutableBit().toString(),
            fileModeSetting()
        )
    }

    /**
     * The value has to come from probing the filesystem. `FS.supportsExecute()`
     * reports platform capability and answers true on Android even where the
     * storage drops the bit, which is the case this whole mechanism exists for.
     */
    @Test
    fun `executable bit support is determined by probing, and the probe cleans up`() {
        val gitDir = git.repository.directory

        val probed = manager.filesystemStoresExecutableBit(gitDir)

        assertEquals(filesystemStoresExecutableBit(), probed)
        assertTrue(
            "the probe must not leave files behind in .git",
            gitDir.listFiles().orEmpty().none { it.name.contains("probe") }
        )
    }

    @Test
    fun `repeated probing is stable`() {
        val gitDir = git.repository.directory
        val first = manager.filesystemStoresExecutableBit(gitDir)
        assertEquals(first, manager.filesystemStoresExecutableBit(gitDir))
        assertEquals(first, manager.filesystemStoresExecutableBit(gitDir))
    }

    @Test
    fun `missing fileMode entry counts as stale and gets written`() = runBlocking {
        // A repository created by command-line git elsewhere may simply not carry
        // the key, in which case git's default (true) applies and the value has to
        // be written explicitly rather than left absent.
        val config = git.repository.config
        config.unset("core", null, "fileMode")
        config.save()
        assertEquals(null, fileModeSetting())

        assertTrue(manager.alignConfigWithFilesystem(repoDir).isSuccess)

        assertEquals(filesystemStoresExecutableBit().toString(), fileModeSetting())
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
