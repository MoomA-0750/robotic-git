package com.example.roboticgit.data.model

data class BranchInfo(
    val name: String,
    val fullName: String,
    val isRemote: Boolean,
    val isCurrent: Boolean,
    val lastCommitHash: String?,
    val lastCommitMessage: String?,
    val lastCommitTime: Long?
)
