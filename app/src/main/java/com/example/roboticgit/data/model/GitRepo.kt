package com.example.roboticgit.data.model

import java.io.File

data class GitRepo(
    val name: String,
    val path: String,
    val localPath: File,
    val isCloning: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = ""
)
