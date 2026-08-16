package com.example.roboticgit.data

import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * Classification of thrown exceptions into [GitError].
 *
 * JGit funnels authorisation failures, missing repositories and dead sockets
 * through the same `TransportException`, so the distinction has to be drawn
 * from the message. These tests use the wordings actually observed against
 * GitHub during on-device testing.
 */
class GitErrorTest {

    @Test
    fun `a missing repository is recognised`() {
        val error = RepositoryNotFoundException("/sdcard/nope").toGitError()
        assertTrue(error is GitError.NotARepository)
    }

    @Test
    fun `an unconfigured remote is recognised`() {
        val error = InvalidRemoteException("origin").toGitError()
        assertTrue(error is GitError.NoRemoteConfigured)
    }

    @Test
    fun `a token without push permission is reported as an auth failure`() {
        // The exact message seen when a fine-grained PAT lacks Contents write.
        val thrown = TransportException(
            "https://github.com/MoomA-0750/rg-test.git: " +
                "git-receive-pack not permitted on 'https://github.com/MoomA-0750/rg-test.git/'"
        )

        val error = thrown.toGitError()

        assertTrue("expected AuthenticationFailed, got $error", error is GitError.AuthenticationFailed)
        assertTrue(
            "the message should tell the user which permission to grant",
            error.message.orEmpty().contains("Contents: Read and write")
        )
    }

    @Test
    fun `a bad token is reported as an auth failure`() {
        val error = TransportException("https://github.com/x/y.git: not authorized").toGitError()
        assertTrue(error is GitError.AuthenticationFailed)
    }

    @Test
    fun `an offline device is reported as a network failure`() {
        val error = UnknownHostException("github.com").toGitError()
        assertTrue(error is GitError.NetworkUnavailable)
    }

    @Test
    fun `a network failure wrapped in a transport exception is still recognised`() {
        val thrown = TransportException(
            "https://github.com/x/y.git",
            IOException("Unable to resolve host \"github.com\"")
        )

        assertTrue(thrown.toGitError() is GitError.NetworkUnavailable)
    }

    @Test
    fun `a rejected push keeps its explanation`() {
        val detail = "main: rejected (non-fast-forward). Pull and merge the remote changes first."

        val error = GitOperationException(detail).toGitError()

        assertTrue(error is GitError.PushRejected)
        assertEquals(detail, error.message)
    }

    @Test
    fun `an operational failure that is not a rejection stays generic`() {
        val error = GitOperationException("Nothing is staged. Select the files first.").toGitError()
        assertTrue(error is GitError.OperationFailed)
    }

    @Test
    fun `an unrecognised failure is preserved rather than swallowed`() {
        val cause = IllegalStateException("something odd")

        val error = cause.toGitError()

        assertTrue(error is GitError.Unexpected)
        assertEquals("something odd", error.message)
        assertSame(cause, error.cause)
    }

    @Test
    fun `classification is idempotent`() {
        val once = UnknownHostException("github.com").toGitError()
        assertSame("re-classifying must not re-wrap", once, once.toGitError())
    }
}
