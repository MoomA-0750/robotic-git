package com.example.roboticgit.data

import com.example.roboticgit.data.model.Account
import com.example.roboticgit.data.model.AccountType

/**
 * Works out which host a git remote points at, and which stored account speaks
 * for that host.
 *
 * Credentials used to be taken from whichever account happened to be first in
 * the list, so a repository hosted on a self-hosted Gitea was pushed with a
 * GitHub token -- sending the token to a third-party server and failing to
 * authenticate at the same time. Matching on host fixes both halves.
 */
object RemoteHost {

    /**
     * The host of [url], including a non-default port, lowercased.
     *
     * Handles the three shapes git remotes come in: `https://host/path`,
     * `ssh://git@host:port/path`, and scp-style `git@host:path`. Returns null
     * for local paths and `file:` URLs, which need no credentials.
     */
    fun of(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("file:", ignoreCase = true)) return null
        if (trimmed.startsWith("/") || trimmed.startsWith(".")) return null

        val schemeSeparator = trimmed.indexOf("://")
        if (schemeSeparator < 0) {
            // scp-style: [user@]host:path. What follows ':' is a path, not a port.
            val atIndex = trimmed.indexOf('@')
            if (atIndex < 0) return null
            return trimmed.substring(atIndex + 1)
                .substringBefore(':')
                .lowercase()
                .takeIf(String::isNotEmpty)
        }

        // scheme://[user@]host[:port]/path
        val authority = trimmed.substring(schemeSeparator + 3)
            .substringBefore('/')
            .substringAfterLast('@')
        if (authority.isEmpty()) return null

        val host = authority.substringBefore(':').lowercase()
        if (host.isEmpty()) return null

        val port = authority.substringAfter(':', "")
        val isDefaultPort = port.isEmpty() || port in DEFAULT_PORTS

        return if (isDefaultPort) host else "$host:$port"
    }

    /** The host this account can authenticate against, or null if unknown. */
    fun of(account: Account): String? = when (account.type) {
        AccountType.GITHUB -> of(account.baseUrl) ?: "github.com"
        AccountType.GITLAB -> of(account.baseUrl) ?: "gitlab.com"
        AccountType.GITEA, AccountType.CUSTOM -> of(account.baseUrl)
    }

    private val DEFAULT_PORTS = setOf("443", "80", "22")
}

/**
 * The account that belongs to [remoteUrl], or null when none does.
 *
 * Deliberately does not fall back to "the first account": handing a token to a
 * host it was not issued for is worse than failing to authenticate.
 */
fun List<Account>.forRemote(remoteUrl: String?): Account? {
    val host = RemoteHost.of(remoteUrl) ?: return null
    return firstOrNull { RemoteHost.of(it) == host }
}
