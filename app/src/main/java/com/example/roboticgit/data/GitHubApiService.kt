package com.example.roboticgit.data

import com.example.roboticgit.data.model.RemoteRepo
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface GitHubApiService {
    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated"
    ): List<RemoteRepo>
}
