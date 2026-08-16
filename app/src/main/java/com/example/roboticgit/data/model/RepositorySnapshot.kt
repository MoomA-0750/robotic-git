package com.example.roboticgit.data.model

/**
 * A commit, reduced to what the UI actually shows.
 *
 * The screens used to hold JGit `RevCommit` instances directly. Those keep a
 * reference to the object reader they were parsed with, so the UI ended up
 * owning objects whose validity depended on a repository handle it knew nothing
 * about -- and the whole log had to stay parsed for as long as the list existed.
 */
data class CommitSummary(
    val id: String,
    val shortMessage: String,
    val fullMessage: String,
    val authorName: String,
    val authorEmail: String,
    /** Commit time in epoch milliseconds. */
    val timestamp: Long
) {
    val abbreviatedId: String get() = id.take(7)

    /** True when the body says more than the subject line already did. */
    val hasBody: Boolean get() = fullMessage.trim() != shortMessage.trim()
}

/**
 * Everything the repository detail screen needs, read in one pass.
 *
 * Gathering these separately meant seven `Git.open` calls -- and seven
 * repository parses -- for every refresh, and a refresh runs on every staging
 * toggle.
 */
data class RepositorySnapshot(
    val commits: List<CommitSummary>,
    val hasMoreCommits: Boolean,
    val fileStatuses: List<FileStatus>,
    val currentBranch: String?,
    val branches: List<BranchInfo>,
    val remotes: List<RemoteInfo>,
    val conflictingFiles: List<String>,
    val isMerging: Boolean
)
