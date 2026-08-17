package com.example.roboticgit.data

import com.example.roboticgit.data.model.Account
import com.example.roboticgit.data.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where each account's repository listing is fetched from.
 *
 * The listing was written for GitHub and then pointed at whatever base URL an
 * account carried, so a self-hosted Gitea was asked for `<instance>/user/repos`
 * and answered 404. The Import tab then showed an empty list with no
 * explanation, which reads as "this account has no repositories".
 */
class ForgeApiTest {

    private fun account(type: AccountType, baseUrl: String? = null) =
        Account(name = "test", type = type, token = "t", baseUrl = baseUrl)

    @Test
    fun `github uses the public api host`() {
        assertEquals(
            "https://api.github.com/",
            account(AccountType.GITHUB).repoListingBaseUrl()
        )
    }

    @Test
    fun `a github enterprise instance uses its own base url`() {
        assertEquals(
            "https://github.example.com/api/v3/",
            account(AccountType.GITHUB, "https://github.example.com/api/v3/").repoListingBaseUrl()
        )
    }

    @Test
    fun `gitea is served under api v1`() {
        assertEquals(
            "http://192.168.1.101:30008/api/v1/",
            account(AccountType.GITEA, "http://192.168.1.101:30008").repoListingBaseUrl()
        )
    }

    @Test
    fun `a trailing slash on the instance url does not double up`() {
        assertEquals(
            "http://192.168.1.101:30008/api/v1/",
            account(AccountType.GITEA, "http://192.168.1.101:30008/").repoListingBaseUrl()
        )
    }

    /** Retrofit rejects a base URL that does not end in a slash. */
    @Test
    fun `every base url ends in a slash`() {
        listOf(
            account(AccountType.GITHUB),
            account(AccountType.GITHUB, "https://github.example.com/api/v3"),
            account(AccountType.GITEA, "https://gitea.example.com"),
            account(AccountType.GITLAB),
            account(AccountType.GITLAB, "https://gitlab.example.com")
        ).forEach {
            val url = it.repoListingBaseUrl()
            assertEquals("$url should end in a slash", true, url?.endsWith("/"))
        }
    }

    @Test
    fun `a gitea account with no instance url has nowhere to ask`() {
        assertNull(account(AccountType.GITEA).repoListingBaseUrl())
    }

    @Test
    fun `gitlab is served under api v4`() {
        assertEquals(
            "https://gitlab.com/api/v4/",
            account(AccountType.GITLAB).repoListingBaseUrl()
        )
    }

    @Test
    fun `a self-hosted gitlab uses its own host`() {
        assertEquals(
            "https://gitlab.example.com/api/v4/",
            account(AccountType.GITLAB, "https://gitlab.example.com/").repoListingBaseUrl()
        )
    }

    /**
     * A custom instance could be running any forge, so there is no listing
     * endpoint to guess at; saying so with null lets the caller skip the request
     * rather than issue one that cannot work.
     */
    @Test
    fun `a custom instance has no listing endpoint to guess`() {
        assertNull(account(AccountType.CUSTOM, "https://git.example.com").repoListingBaseUrl())
    }
}
