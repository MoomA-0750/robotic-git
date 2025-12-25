package com.example.roboticgit.data

object GitHubOAuthConstants {
    const val CLIENT_ID = "YOUR_CLIENT_ID" // 取得したClient IDに置き換えてください
    const val CLIENT_SECRET = "YOUR_CLIENT_SECRET" // 取得したClient Secretに置き換えてください
    const val REDIRECT_URI = "roboticgit://oauth"
    const val AUTH_URL = "https://github.com/login/oauth/authorize"
    const val SCOPES = "repo,user"
}
