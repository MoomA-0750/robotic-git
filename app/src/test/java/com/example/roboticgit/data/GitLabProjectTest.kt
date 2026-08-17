package com.example.roboticgit.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Translating a GitLab project into the repository the rest of the app handles.
 *
 * The sample below is trimmed from a real response from gitlab.com, so the
 * field names and shapes are the ones that actually arrive rather than the ones
 * the documentation implies.
 */
class GitLabProjectTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(body: String): GitLabProject = json.decodeFromString(body)

    @Test
    fun `a project maps onto the fields the app renders`() {
        val project = parse(
            """
            {
              "id": 85461718,
              "name": "rg-gitlab-test",
              "path_with_namespace": "none-group2844034/rg-gitlab-test",
              "http_url_to_repo": "https://gitlab.com/none-group2844034/rg-gitlab-test.git",
              "ssh_url_to_repo": "git@gitlab.com:none-group2844034/rg-gitlab-test.git",
              "visibility": "private",
              "description": null,
              "default_branch": "main"
            }
            """
        )

        val repo = project.toRemoteRepo()

        assertEquals(85461718L, repo.id)
        assertEquals("rg-gitlab-test", repo.name)
        assertEquals("none-group2844034/rg-gitlab-test", repo.fullName)
        assertEquals("https://gitlab.com/none-group2844034/rg-gitlab-test.git", repo.cloneUrl)
        assertNull(repo.description)
    }

    /** GitLab reports visibility as a word; the app shows a public/private badge. */
    @Test
    fun `visibility becomes the private flag`() {
        fun privateFlagFor(visibility: String) = parse(
            """
            {"id":1,"name":"r","path_with_namespace":"o/r",
             "http_url_to_repo":"https://gitlab.com/o/r.git","visibility":"$visibility"}
            """
        ).toRemoteRepo().private

        assertEquals("public is the only thing that is not private", false, privateFlagFor("public"))
        assertEquals(true, privateFlagFor("private"))
        // Visible to anyone signed in to the instance -- not the caller's alone,
        // but not public either.
        assertEquals(true, privateFlagFor("internal"))
    }

    @Test
    fun `a project with no visibility field is treated as not public`() {
        val repo = parse(
            """
            {"id":1,"name":"r","path_with_namespace":"o/r",
             "http_url_to_repo":"https://gitlab.com/o/r.git"}
            """
        ).toRemoteRepo()

        assertTrue(repo.private)
    }

    @Test
    fun `unknown fields in the response are ignored`() {
        // GitLab returns dozens of fields; the parser must not break when they
        // change or when new ones appear.
        val repo = parse(
            """
            {"id":1,"name":"r","path_with_namespace":"o/r",
             "http_url_to_repo":"https://gitlab.com/o/r.git","visibility":"public",
             "star_count":3,"forks_count":0,"_links":{"self":"https://gitlab.com/api/v4/projects/1"}}
            """
        ).toRemoteRepo()

        assertEquals("o/r", repo.fullName)
    }
}
