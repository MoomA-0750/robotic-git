package com.example.roboticgit.data.model

import java.io.File

data class GitRepo(
    val name: String,
    val path: String,
    val localPath: File
)
