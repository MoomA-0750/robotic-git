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
            account(AccountType.GITEA, "https://gitea.example.com")
        ).forEach {
            val url = it.repoListingBaseUrl()
            assertEquals("$url should end in a slash", true, url?.endsWith("/"))
        }
    }

    @Test
    fun `a gitea account with no instance url has nowhere to ask`() {
        assertNull(account(AccountType.GITEA).repoListingBaseUrl())
    }

    /**
     * GitLab's API is shaped differently enough that reusing the GitHub service
     * would silently return nothing; saying so with null lets the caller skip
     * the request instead of pretending the account is empty.
     */
    @Test
    fun `forges without a wired-up listing report none`() {
        assertNull(account(AccountType.GITLAB, "https://gitlab.com").repoListingBaseUrl())
        assertNull(account(AccountType.CUSTOM, "https://git.example.com").repoListingBaseUrl())
    }
}
