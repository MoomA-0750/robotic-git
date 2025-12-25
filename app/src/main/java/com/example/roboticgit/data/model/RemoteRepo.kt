package com.example.roboticgit.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteRepo(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("clone_url") val cloneUrl: String,
    val private: Boolean,
    val description: String? = null
)
