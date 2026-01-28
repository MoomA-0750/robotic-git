package com.example.roboticgit.data.model

enum class MergeStatus {
    SUCCESS,
    FAST_FORWARD,
    ALREADY_UP_TO_DATE,
    CONFLICTING,
    FAILED
}

data class MergeResult(
    val status: MergeStatus,
    val message: String,
    val conflictingFiles: List<String> = emptyList(),
    val mergedCommitHash: String? = null
)
