package com.example.roboticgit.data

import com.example.roboticgit.BuildConfig

object GitHubOAuthConstants {
    val CLIENT_ID = BuildConfig.GITHUB_CLIENT_ID
    val CLIENT_SECRET = BuildConfig.GITHUB_CLIENT_SECRET
    const val REDIRECT_URI = "roboticgit://oauth"
    const val AUTH_URL = "https://github.com/login/oauth/authorize"
    const val SCOPES = "repo,user,admin:repo_hook"
}
