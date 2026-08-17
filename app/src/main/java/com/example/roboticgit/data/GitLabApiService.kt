package com.example.roboticgit.data

import com.example.roboticgit.data.model.RemoteRepo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * GitLab's project listing.
 *
 * It needs its own service because GitLab differs from GitHub in every part of
 * this request, not just the path:
 *
 * - projects live at `projects`, not `user/repos`, and belong to the caller by
 *   way of `membership=true` rather than by the endpoint
 * - ordering is `order_by=last_activity_at`; GitLab's `sort` means only
 *   asc/desc, so GitHub's `sort=updated` is rejected with **400**
 * - the fields are named differently, and visibility is a string rather than a
 *   boolean
 *
 * The `Authorization: Bearer` header does work, so authentication is the one
 * thing that carries over unchanged.
 */
interface GitLabApiService {

    @GET("projects")
    suspend fun getProjects(
        @Header("Authorization") token: String,
        @Query("membership") membership: Boolean = true,
        @Query("per_page") perPage: Int = 100,
        @Query("order_by") orderBy: String = "last_activity_at"
    ): List<GitLabProject>
}

@Serializable
data class GitLabProject(
    val id: Long,
    val name: String,
    @SerialName("path_with_namespace") val pathWithNamespace: String,
    @SerialName("http_url_to_repo") val httpUrlToRepo: String,
    /** One of "private", "internal" or "public". */
    val visibility: String? = null,
    val description: String? = null
)

/**
 * Presents a GitLab project the way the rest of the app expects a repository.
 *
 * "internal" means visible to any signed-in user of the instance, which is not
 * public but is not the caller's alone either. It is grouped with private here
 * because the distinction the UI draws is "can anyone see this".
 */
fun GitLabProject.toRemoteRepo(): RemoteRepo = RemoteRepo(
    id = id,
    name = name,
    fullName = pathWithNamespace,
    cloneUrl = httpUrlToRepo,
    private = visibility != "public",
    description = description
)
