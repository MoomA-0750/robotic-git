package com.example.roboticgit.data

import com.example.roboticgit.data.model.Account
import com.example.roboticgit.data.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Which account gets used for which remote.
 *
 * The failure this prevents is not merely "push does not work": it is a token
 * issued for one host being transmitted to another.
 */
class RemoteHostTest {

    private fun github(name: String = "gh") =
        Account(name = name, type = AccountType.GITHUB, token = "gh-token")

    private fun gitea(baseUrl: String, name: String = "gitea") =
        Account(name = name, type = AccountType.GITEA, token = "gitea-token", baseUrl = baseUrl)

    // ---- URL parsing ----

    @Test
    fun `https url yields its host`() {
        assertEquals("github.com", RemoteHost.of("https://github.com/MoomA-0750/rg-test.git"))
    }

    @Test
    fun `scp style ssh url yields its host`() {
        assertEquals("github.com", RemoteHost.of("git@github.com:MoomA-0750/rg-test.git"))
    }

    @Test
    fun `ssh url with an explicit port keeps the port`() {
        assertEquals("gitea.example.com:2222", RemoteHost.of("ssh://git@gitea.example.com:2222/m/repo.git"))
    }

    @Test
    fun `default ports are dropped so the two url forms agree`() {
        assertEquals("github.com", RemoteHost.of("https://github.com:443/m/repo.git"))
        assertEquals("github.com", RemoteHost.of("http://github.com:80/m/repo.git"))
        assertEquals("github.com", RemoteHost.of("ssh://git@github.com:22/m/repo.git"))
    }

    @Test
    fun `a non default port is part of the identity`() {
        // Gitea on the home server is reached on a port; it must not be confused
        // with anything else served from the same address.
        assertEquals("192.168.1.102:3000", RemoteHost.of("http://192.168.1.102:3000/mooma/notes.git"))
    }

    @Test
    fun `embedded credentials are not mistaken for the host`() {
        assertEquals("github.com", RemoteHost.of("https://user:pass@github.com/m/repo.git"))
    }

    @Test
    fun `hosts are compared case insensitively`() {
        assertEquals("github.com", RemoteHost.of("https://GitHub.COM/m/repo.git"))
    }

    @Test
    fun `local remotes have no host`() {
        assertNull(RemoteHost.of("/srv/git/repo.git"))
        assertNull(RemoteHost.of("file:///srv/git/repo.git"))
        assertNull(RemoteHost.of("../sibling-repo"))
        assertNull(RemoteHost.of(null))
        assertNull(RemoteHost.of(""))
    }

    // ---- Account identity ----

    @Test
    fun `a github account defaults to github com`() {
        assertEquals("github.com", RemoteHost.of(github()))
    }

    @Test
    fun `a self hosted account takes its host from its base url`() {
        assertEquals("git.mooma-0750.xyz", RemoteHost.of(gitea("https://git.mooma-0750.xyz")))
    }

    // ---- Matching ----

    @Test
    fun `the account matching the remote is chosen, not the first one`() {
        val gh = github()
        val self = gitea("http://192.168.1.102:3000")
        val accounts = listOf(gh, self)

        assertSame(self, accounts.forRemote("http://192.168.1.102:3000/mooma/notes.git"))
        assertSame(gh, accounts.forRemote("https://github.com/MoomA-0750/rg-test.git"))
    }

    @Test
    fun `the two url forms select the same account`() {
        val accounts = listOf(github())

        assertSame(
            accounts.forRemote("https://github.com/m/r.git"),
            accounts.forRemote("git@github.com:m/r.git")
        )
    }

    @Test
    fun `an unknown host gets no token rather than someone else's`() {
        val accounts = listOf(github())

        assertNull(
            "a GitHub token must never be sent to another host",
            accounts.forRemote("https://gitlab.com/m/r.git")
        )
    }

    @Test
    fun `a local remote gets no token`() {
        assertNull(listOf(github()).forRemote("/srv/git/repo.git"))
    }

    @Test
    fun `no accounts means no match`() {
        assertNull(emptyList<Account>().forRemote("https://github.com/m/r.git"))
    }
}
