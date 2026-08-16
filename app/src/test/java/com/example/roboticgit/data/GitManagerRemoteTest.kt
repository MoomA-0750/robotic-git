package com.example.roboticgit.data

import com.example.roboticgit.data.TestGitFixtures.BRANCH
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
 * Remote configuration and fetching.
 *
 * The remote's URL is what decides which stored account's token gets sent, so
 * these values are not merely cosmetic -- see [RemoteHostTest].
 */
class GitManagerRemoteTest {

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

    private fun repo() = TestGitFixtures.repoOf(repoDir)

    // ---- Configuration ----

    @Test
    fun `a repository with no remotes lists none`() = runBlocking {
        assertTrue(manager.listRemotes(repo()).getOrThrow().isEmpty())
    }

    @Test
    fun `an added remote is listed with its url`() = runBlocking {
        assertTrue(
            manager.addRemote(repo(), "origin", "https://github.com/MoomA-0750/rg-test.git").isSuccess
        )

        val remote = manager.listRemotes(repo()).getOrThrow().single()
        assertEquals("origin", remote.name)
        assertEquals("https://github.com/MoomA-0750/rg-test.git", remote.fetchUrl)
        assertEquals(
            "with no explicit pushurl, pushing goes to the fetch url",
            "https://github.com/MoomA-0750/rg-test.git",
            remote.pushUrl
        )
    }

    @Test
    fun `several remotes can coexist`() = runBlocking {
        manager.addRemote(repo(), "origin", "https://github.com/MoomA-0750/rg-test.git")
        manager.addRemote(repo(), "home", "http://192.168.1.102:3000/mooma/rg-test.git")

        val remotes = manager.listRemotes(repo()).getOrThrow().associateBy { it.name }

        assertEquals(setOf("origin", "home"), remotes.keys)
        assertEquals("http://192.168.1.102:3000/mooma/rg-test.git", remotes.getValue("home").fetchUrl)
    }

    @Test
    fun `a remote url can be changed`() = runBlocking {
        manager.addRemote(repo(), "origin", "https://github.com/MoomA-0750/old.git")

        assertTrue(
            manager.setRemoteUrl(repo(), "origin", "https://github.com/MoomA-0750/new.git").isSuccess
        )

        assertEquals(
            "https://github.com/MoomA-0750/new.git",
            manager.listRemotes(repo()).getOrThrow().single().fetchUrl
        )
    }

    /**
     * The remote's host selects the credentials, so an edit here changes which
     * token would be transmitted. Worth pinning down explicitly.
     */
    @Test
    fun `changing the remote url changes which account would be used`() = runBlocking {
        val github = com.example.roboticgit.data.model.Account(
            name = "gh",
            type = com.example.roboticgit.data.model.AccountType.GITHUB,
            token = "gh-token"
        )
        val selfHosted = com.example.roboticgit.data.model.Account(
            name = "home",
            type = com.example.roboticgit.data.model.AccountType.GITEA,
            token = "gitea-token",
            baseUrl = "http://192.168.1.102:3000"
        )
        val accounts = listOf(github, selfHosted)

        manager.addRemote(repo(), "origin", "https://github.com/MoomA-0750/rg-test.git")
        assertEquals(github, accounts.forRemote(manager.listRemotes(repo()).getOrThrow().single().pushUrl))

        manager.setRemoteUrl(repo(), "origin", "http://192.168.1.102:3000/mooma/rg-test.git")
        assertEquals(
            selfHosted,
            accounts.forRemote(manager.listRemotes(repo()).getOrThrow().single().pushUrl)
        )
    }

    @Test
    fun `a remote can be removed`() = runBlocking {
        manager.addRemote(repo(), "origin", "https://github.com/MoomA-0750/rg-test.git")

        assertTrue(manager.removeRemote(repo(), "origin").isSuccess)

        assertTrue(manager.listRemotes(repo()).getOrThrow().isEmpty())
    }

    // ---- Fetch ----

    @Test
    fun `fetch brings the remote's commits into the tracking branch`() = runBlocking {
        val remoteDir = File(rootDir, "origin.git")
        val remote = TestGitFixtures.initBareRemote(remoteDir)
        val publisherDir = File(rootDir, "publisher")
        val publisher = TestGitFixtures.initRepoWithCommit(publisherDir)
        try {
            TestGitFixtures.linkAndSeedRemote(publisher, remoteDir)

            val clone = TestGitFixtures.cloneFrom(remoteDir, File(rootDir, "clone"))
            try {
                val cloneRepo = TestGitFixtures.repoOf(File(rootDir, "clone"))
                val before = TestGitFixtures.headId(clone)

                TestGitFixtures.commitFile(publisher, publisherDir, "new.txt", "new\n", "publish")
                publisher.push().setRemote("origin").call()

                assertTrue(manager.fetch(cloneRepo).isSuccess)

                // Fetch updates the remote-tracking ref without touching the
                // working tree or the local branch.
                assertEquals("the local branch does not move", before, TestGitFixtures.headId(clone))
                val tracking = clone.repository.resolve("refs/remotes/origin/$BRANCH")
                assertEquals(
                    "the tracking ref should now hold the published commit",
                    TestGitFixtures.headId(publisher),
                    tracking?.name
                )
            } finally {
                clone.close()
            }
        } finally {
            publisher.close()
            remote.close()
        }
    }

    @Test
    fun `fetching with no remote configured fails`() = runBlocking {
        val result = manager.fetch(repo())

        assertTrue("there is nothing to fetch from", result.isFailure)
    }
}
