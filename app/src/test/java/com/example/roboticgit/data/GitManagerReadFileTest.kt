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
 * Opening a file in the editor, and what its size is allowed to cost.
 *
 * The editor used to read whatever it was pointed at, which meant a generated
 * or vendored file could be loaded in full and then typed into one keystroke at
 * a time. The size now decides first.
 */
class GitManagerReadFileTest {

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

    private fun read(path: String) = runBlocking {
        manager.readFile(TestGitFixtures.repoOf(repoDir), path).getOrThrow()
    }

    private fun writeOfSize(name: String, bytes: Long) {
        val file = File(repoDir, name)
        file.writeText("x".repeat(bytes.toInt()))
    }

    @Test
    fun `a small file comes back editable and complete`() {
        TestGitFixtures.writeFile(repoDir, "small.txt", "hello\n")

        val contents = read("small.txt")

        assertEquals("hello\n", contents.text)
        assertEquals(FileAccess.EDITABLE, contents.access)
        assertEquals(true, contents.isEditable)
    }

    /**
     * Read-only, not truncated: the whole file is still there to read and copy
     * out of. Only writing it back is refused.
     */
    @Test
    fun `a file past the editable limit is readable but not editable`() {
        val size = EditorLimits.MAX_EDITABLE_BYTES + 1
        writeOfSize("generated.txt", size)

        val contents = read("generated.txt")

        assertEquals(FileAccess.READ_ONLY, contents.access)
        assertEquals(false, contents.isEditable)
        assertEquals(size, contents.sizeBytes)
        assertEquals(size, contents.text.length.toLong())
    }

    /**
     * Past the hard limit nothing is read at all. Reporting the size without
     * the bytes is the whole point -- loading them is the failure being
     * avoided, so a test that only checked `access` would pass while the app
     * still paid the cost.
     */
    @Test
    fun `a file past the readable limit is not read`() {
        val size = EditorLimits.MAX_READABLE_BYTES + 1
        writeOfSize("huge.bin", size)

        val contents = read("huge.bin")

        assertEquals(FileAccess.TOO_LARGE, contents.access)
        assertEquals("", contents.text)
        assertEquals(size, contents.sizeBytes)
    }

    @Test
    fun `a missing file is still an error rather than an empty one`() {
        val result = runBlocking {
            manager.readFile(TestGitFixtures.repoOf(repoDir), "nowhere.txt")
        }

        assertTrue("expected a failure, got $result", result.isFailure)
    }
}
