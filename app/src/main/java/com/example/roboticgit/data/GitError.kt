package com.example.roboticgit.data

import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.RefNotFoundException
import org.eclipse.jgit.errors.RepositoryNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * What went wrong, in terms the UI can act on.
 *
 * JGit reports failures as a wide spread of exception types whose messages are
 * written for people reading a stack trace ("git-receive-pack not permitted on
 * ..."). Classifying them once here means the UI can say something useful, and
 * can tell apart the cases that need different responses -- a token problem is
 * fixed in Settings, a rejected push is fixed by pulling first.
 */
sealed class GitError(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** The directory is not a git repository, or is no longer readable. */
    class NotARepository(
        val path: String,
        cause: Throwable? = null
    ) : GitError("Not a git repository: $path", cause)

    /** The token was missing, wrong, or lacked the permission the operation needed. */
    class AuthenticationFailed(
        detail: String?,
        cause: Throwable? = null
    ) : GitError(
        buildString {
            append("Authentication failed. Check the account's token and its permissions")
            append(" (pushing needs Contents: Read and write).")
            if (!detail.isNullOrBlank()) append("\n$detail")
        },
        cause
    )

    /** The remote could not be reached at all. */
    class NetworkUnavailable(
        cause: Throwable? = null
    ) : GitError("Could not reach the remote. Check the network connection.", cause)

    /** No usable remote is configured for this repository. */
    class NoRemoteConfigured(
        cause: Throwable? = null
    ) : GitError("No remote is configured for this repository.", cause)

    /** The remote refused one or more refs; [message] explains which and why. */
    class PushRejected(detail: String, cause: Throwable? = null) : GitError(detail, cause)

    /** A merge or pull stopped because the working tree would be clobbered. */
    class WorkingTreeConflict(
        detail: String?,
        cause: Throwable? = null
    ) : GitError(
        detail ?: "Local changes would be overwritten. Commit or discard them first.",
        cause
    )

    /** A named ref does not exist. */
    class RefNotFound(ref: String, cause: Throwable? = null) :
        GitError("No such branch or ref: $ref", cause)

    /** The operation completed but did not do what was asked; [message] says what. */
    class OperationFailed(detail: String, cause: Throwable? = null) : GitError(detail, cause)

    /** Anything not recognised. Keeps the original for the log. */
    class Unexpected(cause: Throwable) : GitError(
        cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName,
        cause
    )
}

/**
 * Maps a thrown exception onto [GitError].
 *
 * Transport problems are distinguished by message because JGit funnels
 * authorisation failures, missing repositories and dead sockets through the
 * same `TransportException` type.
 */
fun Throwable.toGitError(): GitError {
    if (this is GitError) return this
    if (this is GitOperationException) {
        val text = message.orEmpty()
        return if (text.contains("reject", ignoreCase = true)) {
            GitError.PushRejected(text, this)
        } else {
            GitError.OperationFailed(text, this)
        }
    }

    return when (this) {
        is RepositoryNotFoundException -> GitError.NotARepository(message.orEmpty(), this)
        is InvalidRemoteException -> GitError.NoRemoteConfigured(this)
        is CheckoutConflictException -> GitError.WorkingTreeConflict(message, this)
        is RefNotFoundException -> GitError.RefNotFound(message.orEmpty(), this)
        is UnknownHostException, is SocketTimeoutException, is ConnectException ->
            GitError.NetworkUnavailable(this)
        else -> classifyByMessage()
    }
}

private fun Throwable.classifyByMessage(): GitError {
    val text = message.orEmpty()

    val looksLikeAuth = AUTH_MARKERS.any { text.contains(it, ignoreCase = true) }
    if (looksLikeAuth) return GitError.AuthenticationFailed(text, this)

    val looksLikeNetwork = NETWORK_MARKERS.any { text.contains(it, ignoreCase = true) }
    if (looksLikeNetwork || causeChainHasNetworkFailure()) return GitError.NetworkUnavailable(this)

    if (text.contains("not a git repository", ignoreCase = true)) {
        return GitError.NotARepository(text, this)
    }

    return GitError.Unexpected(this)
}

private fun Throwable.causeChainHasNetworkFailure(): Boolean {
    var current: Throwable? = cause
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        if (current is UnknownHostException ||
            current is SocketTimeoutException ||
            current is ConnectException
        ) {
            return true
        }
        // A bare IOException that names a host is usually connectivity too.
        if (current is IOException &&
            NETWORK_MARKERS.any { current!!.message.orEmpty().contains(it, ignoreCase = true) }
        ) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}

private val AUTH_MARKERS = listOf(
    "not authorized",
    "not permitted",
    "authentication is required",
    "authentication not supported",
    "invalid credentials",
    "401",
    "403"
)

private val NETWORK_MARKERS = listOf(
    "unable to resolve host",
    "connection refused",
    "connection reset",
    "network is unreachable",
    "timed out",
    "failed to connect"
)

private const val MAX_CAUSE_DEPTH = 8
