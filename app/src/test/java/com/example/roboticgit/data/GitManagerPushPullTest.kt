package com.example.roboticgit.data

import com.example.roboticgit.data.TestGitFixtures.BRANCH
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Push and pull reporting.
 *
 * JGit throws for transport- and auth-level failures, but a per-ref rejection
 * (non-fast-forward being the common one) is only reported through the
 * [org.eclipse.jgit.transport.RemoteRefUpdate.Status] carried by the result.
 * Ignoring the return value therefore reports a push that moved nothing as a
 * success -- which is what this suite exists to prevent.
 */
class GitManagerPushPullTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var remoteDir: File
    private lateinit var remote: Git

    private lateinit var alice: Git
    private lateinit var aliceDir: File
    private lateinit var aliceManager: GitManager

    private lateinit var bob: Git
    private lateinit var bobDir: File
    private lateinit var bobManager: GitManager

    @Before
    fun setUp() {
        rootDir = tmp.newFolder("root")
        remoteDir = File(rootDir, "origin.git")
        remote = TestGitFixtures.initBareRemote(remoteDir)

        aliceDir = File(rootDir, "alice")
        alice = TestGitFixtures.initRepoWithCommit(aliceDir)
        TestGitFixtures.linkAndSeedRemote(alice, remoteDir)
        aliceManager = GitManager(rootDir)

        bobDir = File(rootDir, "bob")
        bob = TestGitFixtures.cloneFrom(remoteDir, bobDir)
        bobManager = GitManager(rootDir)
    }

    @After
    fun tearDown() {
        alice.close()
        bob.close()
        remote.close()
    }

    private fun remoteHead(): String? =
        remote.repository.resolve("refs/heads/$BRANCH")?.name

    private fun commitLocally(git: Git, dir: File, name: String, content: String) {
        TestGitFixtures.writeFile(dir, name, content)
        git.add().addFilepattern(name).call()
        git.commit().setMessage("add $name").call()
    }

    @Test
    fun `push advances the remote and reports what moved`() = runBlocking {
        commitLocally(alice, aliceDir, "from-alice.txt", "hello\n")
        val expected = TestGitFixtures.headId(alice)

        val result = aliceManager.push(TestGitFixtures.repoOf(aliceDir))

        assertTrue("push should succeed, got ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals("remote must advance to the local HEAD", expected, remoteHead())

        val outcome = result.getOrThrow()
        assertTrue(
            "a push that moved a ref should report Pushed, got $outcome",
            outcome is PushOutcome.Pushed
        )
        assertTrue(
            "the updated ref should be named, got $outcome",
            (outcome as PushOutcome.Pushed).refs.any { it.contains(BRANCH) }
        )
    }

    @Test
    fun `push with nothing new reports up to date rather than pushed`() = runBlocking {
        val before = remoteHead()

        val outcome = aliceManager.push(TestGitFixtures.repoOf(aliceDir)).getOrThrow()

        assertEquals("remote must not move", before, remoteHead())
        assertTrue("expected UpToDate, got $outcome", outcome is PushOutcome.UpToDate)
    }

    @Test
    fun `non fast forward push is reported as a failure and the remote is unchanged`() = runBlocking {
        // Alice publishes a commit.
        commitLocally(alice, aliceDir, "from-alice.txt", "alice\n")
        assertTrue(aliceManager.push(TestGitFixtures.repoOf(aliceDir)).isSuccess)
        val remoteAfterAlice = remoteHead()
        assertNotNull(remoteAfterAlice)

        // Bob, who never fetched, commits on top of the older history.
        commitLocally(bob, bobDir, "from-bob.txt", "bob\n")

        val result = bobManager.push(TestGitFixtures.repoOf(bobDir))

        assertTrue(
            "a rejected push must not be reported as success (got ${result.getOrNull()})",
            result.isFailure
        )
        assertEquals(
            "the remote must be untouched by a rejected push",
            remoteAfterAlice,
            remoteHead()
        )

        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            "the failure should explain the rejection, got: $message",
            message.contains("reject", ignoreCase = true) ||
                message.contains("fast-forward", ignoreCase = true) ||
                message.contains("fast forward", ignoreCase = true)
        )
    }

    @Test
    fun `push without any configured remote fails instead of silently doing nothing`() = runBlocking {
        val loneDir = File(rootDir, "lone")
        val lone = TestGitFixtures.initRepoWithCommit(loneDir)
        try {
            val result = GitManager(rootDir).push(TestGitFixtures.repoOf(loneDir))
            assertTrue(
                "pushing with no remote configured should not look like a success",
                result.isFailure
            )
        } finally {
            lone.close()
        }
    }

    @Test
    fun `pull fast forwards and reports the merge`() = runBlocking {
        commitLocally(alice, aliceDir, "from-alice.txt", "alice\n")
        aliceManager.push(TestGitFixtures.repoOf(aliceDir))

        val result = bobManager.pull(TestGitFixtures.repoOf(bobDir))

        assertTrue("pull should succeed, got ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(
            "bob should now be at the remote head",
            remoteHead(),
            TestGitFixtures.headId(bob)
        )
        assertEquals("alice\n", TestGitFixtures.readFile(bobDir, "from-alice.txt"))
    }

    @Test
    fun `conflicting pull is reported as a failure`() = runBlocking {
        // Both sides change the same file in incompatible ways.
        TestGitFixtures.writeFile(aliceDir, "shared.txt", "alice version\n")
        alice.add().addFilepattern("shared.txt").call()
        alice.commit().setMessage("alice edits shared").call()
        aliceManager.push(TestGitFixtures.repoOf(aliceDir))

        TestGitFixtures.writeFile(bobDir, "shared.txt", "bob version\n")
        bob.add().addFilepattern("shared.txt").call()
        bob.commit().setMessage("bob edits shared").call()

        val result = bobManager.pull(TestGitFixtures.repoOf(bobDir))

        assertTrue(
            "a conflicting pull must not be reported as success",
            result.isFailure
        )
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            "the failure should mention the conflict, got: $message",
            message.contains("conflict", ignoreCase = true)
        )
    }
}
