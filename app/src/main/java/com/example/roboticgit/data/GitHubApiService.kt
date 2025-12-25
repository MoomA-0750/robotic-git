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
    @SerialName("id") val id: Long
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
