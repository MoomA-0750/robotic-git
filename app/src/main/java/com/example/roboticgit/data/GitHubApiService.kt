package com.example.roboticgit.data

import com.example.roboticgit.data.model.RemoteRepo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.*

@Serializable
data class GitHubUser(
    @SerialName("login") val login: String,
    @SerialName("id") val id: Long,
    @SerialName("avatar_url") val avatarUrl: String
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

}
