package com.example.roboticgit.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AccountType {
    GITHUB, GITEA
}

@Serializable
data class Account(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: AccountType,
    val token: String,
    val avatarUrl: String? = null,
    val baseUrl: String? = null // For Gitea
)
