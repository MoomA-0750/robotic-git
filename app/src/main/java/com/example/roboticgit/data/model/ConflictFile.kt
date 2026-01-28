package com.example.roboticgit.data.model

data class ConflictFile(
    val path: String,
    val oursContent: String,
    val theirsContent: String,
    val baseContent: String?,
    val conflictMarkers: List<ConflictRegion> = emptyList()
)

data class ConflictRegion(
    val startLine: Int,
    val endLine: Int,
    val oursLines: List<String>,
    val theirsLines: List<String>,
    val baseLines: List<String>? = null
)
