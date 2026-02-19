package com.example.roboticgit.data

import com.example.roboticgit.data.model.RemoteRepo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.*

@Serializable
data class GitHubTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("scope") val scope: String
)

@Serializable
data class GitHubUser(
    @SerialName("login") val login: String,
    @SerialName("id") val id: Long,
    @SerialName("avatar_url") val avatarUrl: String
)

// Gitea API user response (compatible with Forgejo)
@Serializable
data class GiteaUser(
    @SerialName("login") val login: String,
    @SerialName("id") val id: Long,
    @SerialName("avatar_url") val avatarUrl: String
)

// GitLab API user response
@Serializable
data class GitLabUser(
    @SerialName("username") val username: String,
    @SerialName("id") val id: Long,
    @SerialName("avatar_url") val avatarUrl: String
)

// Gitea repo response
@Serializable
data class GiteaRepo(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("clone_url") val cloneUrl: String,
    val private: Boolean,
    val description: String? = null
)

// GitLab repo (project) response
@Serializable
data class GitLabProject(
    val id: Long,
    val name: String,
    @SerialName("path_with_namespace") val fullName: String,
    @SerialName("http_url_to_repo") val cloneUrl: String,
    val description: String? = null,
    val visibility: String? = null
)

interface GitHubApiService {
    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated"
    ): List<RemoteRepo>

    @GET("user")
    suspend fun getUser(
        @Header("Authorization") token: String
    ): GitHubUser

    @Headers("Accept: application/json")
    @POST("https://github.com/login/oauth/access_token")
    @FormUrlEncoded
    suspend fun getAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String
    ): GitHubTokenResponse
}

// Gitea/Forgejo API service
interface GiteaApiService {
    @GET("api/v1/user")
    suspend fun getUser(
        @Header("Authorization") token: String
    ): GiteaUser

    @GET("api/v1/user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50,
        @Query("sort") sort: String = "updated"
    ): List<GiteaRepo>
}

// GitLab API service
interface GitLabApiService {
    @GET("api/v4/user")
    suspend fun getUser(
        @Header("PRIVATE-TOKEN") token: String
    ): GitLabUser

    @GET("api/v4/projects")
    suspend fun getUserProjects(
        @Header("PRIVATE-TOKEN") token: String,
        @Query("membership") membership: Boolean = true,
        @Query("per_page") perPage: Int = 100,
        @Query("order_by") orderBy: String = "updated_at"
    ): List<GitLabProject>
}
