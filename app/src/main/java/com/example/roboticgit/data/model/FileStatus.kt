package com.example.roboticgit.data.model

enum class FileState {
    MODIFIED, UNTRACKED, ADDED, REMOVED, DELETED, MISSING, RENAMED, COPIED, CONFLICTING
}

data class FileStatus(
    val path: String,
    val state: FileState,
    val isStaged: Boolean
)
