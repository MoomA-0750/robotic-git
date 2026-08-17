package com.example.roboticgit.data

import com.example.roboticgit.data.model.BranchInfo
import com.example.roboticgit.data.model.ConflictFile
import org.eclipse.jgit.util.io.DisabledOutputStream
import com.example.roboticgit.data.model.RepositorySnapshot
import com.example.roboticgit.data.model.CommitSummary
import com.example.roboticgit.data.model.ConflictRegion
import com.example.roboticgit.data.model.FileState
import com.example.roboticgit.data.model.FileStatus
import com.example.roboticgit.data.model.GitRepo
import com.example.roboticgit.data.model.MergeResult
import com.example.roboticgit.data.model.MergeStatus
import com.example.roboticgit.data.model.RemoteInfo
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult as JGitMergeResult
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.api.PullResult
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GitManager(private val rootDir: File) {

    init {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    suspend fun listRepositories(extraPaths: Set<String> = emptySet()): List<GitRepo> = withContext(Dispatchers.IO) {
        val repos = mutableListOf<GitRepo>()
        
        // Scan the default root directory
        rootDir.listFiles()?.forEach { file ->
            if (file.isDirectory && File(file, ".git").exists()) {
                val repo = getRepoFromDirectory(file)
                if (repo != null) repos.add(repo)
            }
        }

        // Add repositories from extra tracked paths
        extraPaths.forEach { path ->
            val file = File(path)
            if (file.exists() && file.isDirectory && File(file, ".git").exists()) {
                // Avoid duplicates by comparing absolute paths
                if (repos.none { it.localPath.absolutePath == file.absolutePath }) {
                    val repo = getRepoFromDirectory(file)
                    if (repo != null) repos.add(repo)
                }
            }
        }
        
        repos.sortedBy { it.name }
    }

    private fun getRepoFromDirectory(directory: File): GitRepo? {
        return try {
            val lastCommitTime = Git.open(directory).use { git ->
                val head = git.repository.resolve(Constants.HEAD)
                if (head != null) {
                    val revWalk = RevWalk(git.repository)
                    val commit = revWalk.parseCommit(head)
                    commit.commitTime * 1000L // convert to milliseconds
                } else 0L
            }
            GitRepo(directory.name, directory.absolutePath, directory, lastCommitTime = lastCommitTime)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun cloneRepository(
        url: String, 
        name: String, 
        token: String? = null,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<GitRepo> = withContext(Dispatchers.IO) {
        val destination = File(rootDir, name)
        if (destination.exists()) {
             return@withContext Result.failure(Exception("Directory already exists"))
        }

        try {
            val monitor = object : ProgressMonitor {
                private var totalWork = 0
                private var completedWork = 0
                private var currentTask = ""

                override fun start(totalTasks: Int) {}
                override fun beginTask(title: String, totalWork: Int) {
                    this.currentTask = title
                    this.totalWork = totalWork
                    this.completedWork = 0
                    onProgress(currentTask, 0f)
                }
                override fun update(completed: Int) {
                    completedWork += completed
                    if (totalWork > 0) {
                        onProgress(currentTask, completedWork.toFloat() / totalWork)
                    }
                }
                override fun endTask() {
                    onProgress(currentTask, 1f)
                }
                override fun isCancelled(): Boolean = false
                override fun showDuration(enabled: Boolean) {}
            }

            val cloneCommand = Git.cloneRepository()
                .setURI(url)
                .setDirectory(destination)
                .setProgressMonitor(monitor)
            
            if (!token.isNullOrBlank()) {
                cloneCommand.setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
            }

            cloneCommand.call().use { git ->
                val head = git.repository.resolve(Constants.HEAD)
                val lastCommitTime = if (head != null) {
                    val revWalk = RevWalk(git.repository)
                    val commit = revWalk.parseCommit(head)
                    commit.commitTime * 1000L
                } else 0L
                Result.success(GitRepo(name, destination.absolutePath, destination, lastCommitTime = lastCommitTime))
            }
        } catch (e: GitAPIException) {
            Result.failure(e.toGitError())
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun fetch(repo: GitRepo, token: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val fetchCommand = git.fetch()
                if (!token.isNullOrBlank()) {
                    fetchCommand.setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
                }
                fetchCommand.call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    /**
     * Reads everything the repository detail screen shows, from a single open.
     *
     * The screen used to assemble this from seven separate calls, each of which
     * opened and parsed the repository again -- on every refresh, and a refresh
     * runs on every staging toggle.
     *
     * [commitLimit] bounds the log; the result reports whether it was cut off.
     */
    suspend fun loadSnapshot(repo: GitRepo, commitLimit: Int): Result<RepositorySnapshot> =
        withContext(Dispatchers.IO) {
            try {
                Git.open(repo.localPath).use { git ->
                    val repository = git.repository
                    val status = git.status().call()
                    val conflicting = status.conflicting.toList()

                    // One extra tells us the log was truncated without a second walk.
                    val fetched = if (repository.resolve(Constants.HEAD) == null) {
                        emptyList()
                    } else {
                        git.log().setMaxCount(commitLimit + 1).call().toList()
                    }

                    val state = repository.repositoryState

                    Result.success(
                        RepositorySnapshot(
                            commits = fetched.take(commitLimit).map { it.toSummary() },
                            hasMoreCommits = fetched.size > commitLimit,
                            fileStatuses = buildFileStatuses(status),
                            currentBranch = repository.branch,
                            branches = readBranches(git),
                            remotes = readRemotes(git),
                            conflictingFiles = conflicting,
                            isMerging = state == RepositoryState.MERGING ||
                                state == RepositoryState.MERGING_RESOLVED
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e.toGitError())
            }
        }

    suspend fun getFileStatuses(repo: GitRepo): Result<List<FileStatus>> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                Result.success(buildFileStatuses(git.status().call()))
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    /**
     * Flattens JGit's status buckets into the flat list the Changes tab renders.
     *
     * `modified` (index vs working tree) and `changed` (HEAD vs index) overlap by
     * design: a file staged and then edited again belongs in both, and git itself
     * lists it under "to be committed" and "not staged" at once. The two rows are
     * correct -- do not de-duplicate them.
     */
    private fun buildFileStatuses(status: org.eclipse.jgit.api.Status): List<FileStatus> {
        val result = mutableListOf<FileStatus>()

        // Conflicting files take priority
        status.conflicting.forEach { result.add(FileStatus(it, FileState.CONFLICTING, false)) }

        // Filter out conflicting files from other categories
        val conflictingPaths = status.conflicting.toSet()

        status.modified.filterNot { it in conflictingPaths }.forEach { result.add(FileStatus(it, FileState.MODIFIED, false)) }
        status.untracked.filterNot { it in conflictingPaths }.forEach { result.add(FileStatus(it, FileState.UNTRACKED, false)) }
        status.missing.filterNot { it in conflictingPaths }.forEach { result.add(FileStatus(it, FileState.MISSING, false)) }

        status.changed.filterNot { it in conflictingPaths }.forEach { result.add(FileStatus(it, FileState.MODIFIED, true)) }
        status.added.filterNot { it in conflictingPaths }.forEach { result.add(FileStatus(it, FileState.ADDED, true)) }
        status.removed.filterNot { it in conflictingPaths }.forEach { result.add(FileStatus(it, FileState.REMOVED, true)) }

        return result.sortedBy { it.path }
    }

    private fun RevCommit.toSummary(): CommitSummary = CommitSummary(
        id = name,
        shortMessage = shortMessage,
        fullMessage = fullMessage,
        authorName = authorIdent?.name.orEmpty(),
        authorEmail = authorIdent?.emailAddress.orEmpty(),
        timestamp = commitTime * 1000L
    )

    suspend fun getFileDiff(repo: GitRepo, fileStatus: FileStatus): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val repoObj = git.repository
                val hasCommits = repoObj.resolve(Constants.HEAD) != null

                if (fileStatus.state == FileState.UNTRACKED || (!hasCommits && fileStatus.state == FileState.ADDED)) {
                    val file = File(repo.localPath, fileStatus.path)
                    if (file.exists()) {
                        val content = try { file.readText() } catch (e: Exception) { "" }
                        return@withContext Result.success(content.lines().joinToString("\n") { "+$it" })
                    }
                }

                val out = ByteArrayOutputStream()
                DiffFormatter(out).use { formatter ->
                    formatter.setRepository(repoObj)
                    formatter.setContext(3)
                    formatter.setPathFilter(PathFilter.create(fileStatus.path))

                    val diffs = if (fileStatus.isStaged) {
                        if (hasCommits) {
                            val headTree = CanonicalTreeParser()
                            repoObj.newObjectReader().use { reader ->
                                val headId = repoObj.resolve(Constants.HEAD + "^{tree}")
                                headTree.reset(reader, headId)
                            }
                            val indexTree = DirCacheIterator(repoObj.readDirCache())
                            formatter.scan(headTree, indexTree)
                        } else {
                            emptyList()
                        }
                    } else {
                        val indexTree = DirCacheIterator(repoObj.readDirCache())
                        val workTree = FileTreeIterator(repoObj)
                        formatter.scan(indexTree, workTree)
                    }

                    if (diffs.isEmpty()) {
                        return@withContext Result.success("No changes detected.")
                    }

                    for (entry in diffs) {
                        formatter.format(entry)
                    }
                    
                    val diffResult = out.toString("UTF-8")
                    Result.success(diffResult.ifBlank { "No changes detected." })
                }
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    /**
     * Paths touched by [commitId].
     *
     * Takes an id rather than a `RevCommit` so the UI never has to hold a JGit
     * object whose backing reader belongs to a repository handle that closed
     * long ago.
     */
    suspend fun getCommitChanges(repo: GitRepo, commitId: String): List<CommitChange> =
        withContext(Dispatchers.IO) {
            try {
                Git.open(repo.localPath).use { git ->
                    val repository = git.repository
                    // use{}: the reader owns pack windows; the previous version
                    // opened one per call and never closed it.
                    repository.newObjectReader().use { reader ->
                        RevWalk(repository).use { walk ->
                            val commit = walk.parseCommit(repository.resolve(commitId))
                            val oldTree = parentTreeOf(commit, reader, walk)
                            val newTree = CanonicalTreeParser().apply { reset(reader, commit.tree) }

                            DiffFormatter(DisabledOutputStream.INSTANCE).use { formatter ->
                                formatter.setRepository(repository)
                                formatter.scan(oldTree, newTree).map { entry ->
                                    CommitChange(
                                        path = if (entry.changeType == DiffEntry.ChangeType.DELETE) {
                                            entry.oldPath
                                        } else {
                                            entry.newPath
                                        },
                                        changeType = entry.changeType.name
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun getCommitFileDiff(repo: GitRepo, commitId: String, path: String): String =
        withContext(Dispatchers.IO) {
            try {
                Git.open(repo.localPath).use { git ->
                    val repository = git.repository
                    val out = ByteArrayOutputStream()
                    repository.newObjectReader().use { reader ->
                        RevWalk(repository).use { walk ->
                            val commit = walk.parseCommit(repository.resolve(commitId))
                            DiffFormatter(out).use { formatter ->
                                formatter.setRepository(repository)
                                formatter.setPathFilter(PathFilter.create(path))

                                val oldTree = parentTreeOf(commit, reader, walk)
                                val newTree = CanonicalTreeParser().apply { reset(reader, commit.tree) }

                                formatter.scan(oldTree, newTree).forEach { formatter.format(it) }
                                out.toString("UTF-8")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                "Error loading diff: ${e.toGitError().message}"
            }
        }

    /** The first parent's tree, or the empty tree for a root commit. */
    private fun parentTreeOf(
        commit: RevCommit,
        reader: org.eclipse.jgit.lib.ObjectReader,
        walk: RevWalk
    ): CanonicalTreeParser = CanonicalTreeParser().apply {
        if (commit.parentCount > 0) {
            reset(reader, walk.parseCommit(commit.getParent(0)).tree)
        } else {
            reset()
        }
    }

    /**
     * Reads a file for the editor, with [EditorLimits] deciding what may be
     * done with it. A file past the hard limit is not read at all -- its size
     * comes back instead, so the caller can say why rather than stall on it.
     */
    suspend fun readFile(repo: GitRepo, path: String): Result<FileContents> = withContext(Dispatchers.IO) {
        try {
            val file = File(repo.localPath, path)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File not found"))
            }
            val size = file.length()
            val access = EditorLimits.accessFor(size)
            val text = if (access == FileAccess.TOO_LARGE) "" else file.readText()
            Result.success(FileContents(text = text, sizeBytes = size, access = access))
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun writeFile(repo: GitRepo, path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(repo.localPath, path)
            file.writeText(content)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun listFiles(repo: GitRepo, relativePath: String = ""): List<RepoFile> = withContext(Dispatchers.IO) {
        val targetDir = if (relativePath.isEmpty()) repo.localPath else File(repo.localPath, relativePath)
        val result = mutableListOf<RepoFile>()
        targetDir.listFiles()?.forEach { file ->
            if (file.name == ".git") return@forEach
            result.add(
                RepoFile(
                    name = file.name,
                    path = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}",
                    isDirectory = file.isDirectory
                )
            )
        }
        result.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }

    suspend fun stageFile(repo: GitRepo, path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.add().addFilepattern(path).call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun unstageFile(repo: GitRepo, path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.reset().addPath(path).call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun rollbackFile(repo: GitRepo, path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.checkout().addPath(path).call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun commit(
        repo: GitRepo, 
        message: String,
        authorName: String? = null,
        authorEmail: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                // Deliberately no implicit "add ." here: the Changes tab lets the
                // user choose what to stage, and staging everything behind their
                // back would commit work they left out on purpose.
                // setAllowEmpty(false) turns "nothing staged" into a reported
                // failure instead of a silent empty commit.
                val commitCommand = git.commit()
                    .setMessage(message)
                    .setAllowEmpty(false)

                if (!authorName.isNullOrBlank() && !authorEmail.isNullOrBlank()) {
                    commitCommand.setAuthor(authorName, authorEmail)
                    commitCommand.setCommitter(authorName, authorEmail)
                }
                
                commitCommand.call()
                Result.success(Unit)
            }
        } catch (e: EmptyCommitException) {
            // JGit's own wording here is just "No changes", which does not tell the
            // user that the fix is to stage something.
            Result.failure(
                GitOperationException("Nothing is staged. Select the files you want to include first.")
            )
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun push(repo: GitRepo, token: String? = null): Result<PushOutcome> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val pushCommand = git.push()
                if (!token.isNullOrBlank()) {
                    pushCommand.setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
                }

                // JGit throws only for transport- and auth-level problems. A ref the
                // remote refuses -- non-fast-forward above all -- comes back merely
                // as a status on the result. Discarding the result therefore makes a
                // push that transferred nothing indistinguishable from one that
                // worked, which is exactly the "display diverges from reality" bug.
                val results = pushCommand.call().toList()
                val updates = results.flatMap { it.remoteUpdates }

                if (updates.isEmpty()) {
                    return@use Result.failure(
                        GitOperationException(
                            "Nothing was pushed. This branch has no matching branch on the remote yet."
                        )
                    )
                }

                val rejected = updates.filterNot { it.status in ACCEPTED_PUSH_STATUSES }
                if (rejected.isNotEmpty()) {
                    return@use Result.failure(GitOperationException(describePushRejection(rejected, results)))
                }

                val advanced = updates.filter { it.status == RemoteRefUpdate.Status.OK }
                if (advanced.isEmpty()) {
                    Result.success(PushOutcome.UpToDate)
                } else {
                    Result.success(PushOutcome.Pushed(advanced.map { shortenRefName(it.remoteName) }))
                }
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun pull(repo: GitRepo, token: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val pullCommand = git.pull()
                if (!token.isNullOrBlank()) {
                    pullCommand.setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
                }

                // As with push, a merge that stops on conflicts is a normal return
                // value rather than an exception.
                val result = pullCommand.call()
                if (result.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(GitOperationException(describePullFailure(result)))
                }
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    private fun shortenRefName(ref: String): String =
        ref.removePrefix("refs/heads/").removePrefix("refs/tags/")

    private fun describePushRejection(
        rejected: List<RemoteRefUpdate>,
        results: List<PushResult>
    ): String {
        val details = rejected.joinToString("; ") { update ->
            val reason = when (update.status) {
                RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD ->
                    "rejected (non-fast-forward). Pull and merge the remote changes first."
                RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED ->
                    "rejected because the remote ref moved since it was last fetched."
                RemoteRefUpdate.Status.REJECTED_NODELETE ->
                    "rejected: the remote refused to delete this ref."
                RemoteRefUpdate.Status.REJECTED_OTHER_REASON ->
                    "rejected: ${update.message ?: "no reason given"}"
                RemoteRefUpdate.Status.NON_EXISTING ->
                    "the remote ref does not exist."
                RemoteRefUpdate.Status.NOT_ATTEMPTED ->
                    "not attempted."
                RemoteRefUpdate.Status.AWAITING_REPORT ->
                    "the remote never reported the outcome."
                else -> update.status.name
            }
            "${shortenRefName(update.remoteName)}: $reason"
        }

        val remoteMessages = results
            .mapNotNull { it.messages?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString("\n")

        return if (remoteMessages.isEmpty()) details else "$details\n$remoteMessages"
    }

    private fun describePullFailure(result: PullResult): String {
        result.mergeResult?.let { merge ->
            val conflicts = merge.conflicts?.keys.orEmpty()
            if (conflicts.isNotEmpty()) {
                return "Pull stopped with conflicts in: ${conflicts.joinToString(", ")}"
            }
            val failing = merge.failingPaths?.entries
                ?.joinToString(", ") { "${it.key} (${it.value})" }
            if (!failing.isNullOrBlank()) {
                return "Pull could not update: $failing"
            }
            return "Pull failed: ${merge.mergeStatus}"
        }
        result.rebaseResult?.let { rebase ->
            val conflicts = rebase.conflicts?.joinToString(", ")
            if (!conflicts.isNullOrBlank()) {
                return "Pull (rebase) stopped with conflicts in: $conflicts"
            }
            return "Pull (rebase) failed: ${rebase.status}"
        }
        return "Pull failed"
    }

    /**
     * Aligns a repository's config with what the underlying filesystem can
     * actually represent.
     *
     * Android's emulated storage (`/sdcard`) silently drops the executable bit,
     * so with `core.fileMode` left on, every already-executable tracked file
     * reports as permanently MODIFIED. JGit probes this itself when it clones,
     * but a repository created elsewhere and merely opened by the app keeps
     * whatever its original filesystem supported. Call this when registering
     * such a repository.
     *
     * Writes only when the value actually differs, so it is cheap to repeat.
     */
    suspend fun alignConfigWithFilesystem(directory: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(directory).use { git ->
                val config = git.repository.config
                val storesExecutableBit = filesystemStoresExecutableBit(git.repository.directory)
                val currentFileMode = config.getString("core", null, "fileMode")?.toBooleanStrictOrNull()

                if (currentFileMode != storesExecutableBit) {
                    config.setBoolean("core", null, "fileMode", storesExecutableBit)
                    if (!storesExecutableBit) {
                        // The same storage cannot hold symlinks either, and leaving
                        // this on turns every tracked symlink into a phantom change.
                        config.setBoolean("core", null, "symlinks", false)
                    }
                    config.save()
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    /**
     * Tests whether [probeDirectory]'s filesystem actually keeps the executable
     * bit, by setting it on a scratch file and reading it back.
     *
     * `FS.supportsExecute()` reports what the *platform* can do, and on Android it
     * answers true even though the emulated storage backing `/sdcard` silently
     * drops the bit. Asking the filesystem directly is the only reliable answer.
     * The probe file goes in the `.git` directory so a crash mid-probe cannot
     * leave a stray untracked file in the user's working tree.
     */
    internal fun filesystemStoresExecutableBit(probeDirectory: File): Boolean {
        val probe = File(probeDirectory, "roboticgit-exec-probe.tmp")
        return try {
            probe.delete()
            if (!probe.createNewFile()) return FS.DETECTED.supportsExecute()
            probe.setExecutable(true, true) && probe.canExecute()
        } catch (e: Exception) {
            FS.DETECTED.supportsExecute()
        } finally {
            probe.delete()
        }
    }

    suspend fun getCurrentBranch(repo: GitRepo): String? = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.repository.branch
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun listBranches(repo: GitRepo): Result<List<BranchInfo>> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                Result.success(readBranches(git))
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    private fun readBranches(git: Git): List<BranchInfo> {
        val currentBranch = git.repository.branch

        val localBranches = git.branchList().call()
        val remoteBranches = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()

        // use{}: the walk holds an ObjectReader, and leaking one per call kept
        // pack windows alive for the lifetime of the process.
        RevWalk(git.repository).use { revWalk ->
            return (localBranches.map { it to false } + remoteBranches.map { it to true })
                .map { (ref, isRemote) ->
                    val fullName = ref.name
                    val name = if (isRemote) {
                        fullName.removePrefix("refs/remotes/")
                    } else {
                        fullName.removePrefix("refs/heads/")
                    }

                    val commit = revWalk.parseCommit(ref.objectId)

                    BranchInfo(
                        name = name,
                        fullName = fullName,
                        isRemote = isRemote,
                        isCurrent = !isRemote && name == currentBranch,
                        lastCommitHash = commit.name,
                        lastCommitMessage = commit.shortMessage,
                        lastCommitTime = commit.commitTime * 1000L
                    )
                }
        }
    }

    suspend fun createBranch(repo: GitRepo, branchName: String, startPoint: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val command = git.branchCreate().setName(branchName)
                if (startPoint != null) {
                    command.setStartPoint(startPoint)
                }
                command.call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun deleteBranch(repo: GitRepo, branchName: String, force: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.branchDelete()
                    .setBranchNames(branchName)
                    .setForce(force)
                    .call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun checkoutBranch(repo: GitRepo, branchName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.checkout().setName(branchName).call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun hasUncommittedChanges(repo: GitRepo): Boolean = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val status = git.status().call()
                !status.isClean
            }
        } catch (e: Exception) {
            false
        }
    }

    // ========== Task 5: Merge functionality ==========

    suspend fun mergeBranch(
        repo: GitRepo,
        branchName: String,
        fastForwardOnly: Boolean = false,
        commitMessage: String? = null
    ): MergeResult = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val targetRef = git.repository.resolve(branchName)
                    ?: return@withContext MergeResult(
                        status = MergeStatus.FAILED,
                        message = "Branch '$branchName' not found"
                    )

                val mergeCommand = git.merge()
                    .include(targetRef)
                    .setCommit(true)

                if (fastForwardOnly) {
                    mergeCommand.setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
                }

                if (commitMessage != null) {
                    mergeCommand.setMessage(commitMessage)
                }

                val result = mergeCommand.call()

                when (result.mergeStatus) {
                    JGitMergeResult.MergeStatus.ALREADY_UP_TO_DATE -> MergeResult(
                        status = MergeStatus.ALREADY_UP_TO_DATE,
                        message = "Already up to date"
                    )
                    JGitMergeResult.MergeStatus.FAST_FORWARD -> MergeResult(
                        status = MergeStatus.FAST_FORWARD,
                        message = "Fast-forward merge completed",
                        mergedCommitHash = result.newHead?.name
                    )
                    JGitMergeResult.MergeStatus.MERGED -> MergeResult(
                        status = MergeStatus.SUCCESS,
                        message = "Merge completed successfully",
                        mergedCommitHash = result.newHead?.name
                    )
                    JGitMergeResult.MergeStatus.CONFLICTING -> {
                        val conflicts = result.conflicts?.keys?.toList() ?: emptyList()
                        MergeResult(
                            status = MergeStatus.CONFLICTING,
                            message = "Merge has conflicts that need to be resolved",
                            conflictingFiles = conflicts
                        )
                    }
                    else -> MergeResult(
                        status = MergeStatus.FAILED,
                        message = "Merge failed: ${result.mergeStatus}"
                    )
                }
            }
        } catch (e: Exception) {
            MergeResult(
                status = MergeStatus.FAILED,
                message = "Merge failed: ${e.message}"
            )
        }
    }

    suspend fun isMerging(repo: GitRepo): Boolean = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val state = git.repository.repositoryState
                state == RepositoryState.MERGING || state == RepositoryState.MERGING_RESOLVED
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun abortMerge(repo: GitRepo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    // ========== Task 6: Remote management ==========

    suspend fun listRemotes(repo: GitRepo): Result<List<RemoteInfo>> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                Result.success(readRemotes(git))
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    private fun readRemotes(git: Git): List<RemoteInfo> {
        val config = git.repository.config
        return config.getSubsections("remote").map { name ->
            RemoteInfo(
                name = name,
                fetchUrl = config.getString("remote", name, "url") ?: "",
                pushUrl = config.getString("remote", name, "pushurl")
                    ?: config.getString("remote", name, "url") ?: ""
            )
        }
    }

    suspend fun addRemote(repo: GitRepo, name: String, url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.remoteAdd()
                    .setName(name)
                    .setUri(org.eclipse.jgit.transport.URIish(url))
                    .call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun removeRemote(repo: GitRepo, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.remoteRemove().setRemoteName(name).call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun setRemoteUrl(repo: GitRepo, name: String, url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.remoteSetUrl()
                    .setRemoteName(name)
                    .setRemoteUri(org.eclipse.jgit.transport.URIish(url))
                    .call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    // ========== Task 7: Conflict detection ==========

    suspend fun getConflictingFiles(repo: GitRepo): List<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val status = git.status().call()
                status.conflicting.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getConflictContent(repo: GitRepo, filePath: String): ConflictFile? = withContext(Dispatchers.IO) {
        try {
            val file = File(repo.localPath, filePath)
            if (!file.exists()) return@withContext null

            val content = file.readText()
            val lines = content.lines()

            val regions = mutableListOf<ConflictRegion>()
            var oursLines = mutableListOf<String>()
            var theirsLines = mutableListOf<String>()
            var inOurs = false
            var inTheirs = false
            var regionStart = -1

            lines.forEachIndexed { index, line ->
                when {
                    line.startsWith("<<<<<<<") -> {
                        inOurs = true
                        regionStart = index
                        oursLines = mutableListOf()
                    }
                    line.startsWith("=======") && inOurs -> {
                        inOurs = false
                        inTheirs = true
                        theirsLines = mutableListOf()
                    }
                    line.startsWith(">>>>>>>") && inTheirs -> {
                        inTheirs = false
                        regions.add(
                            ConflictRegion(
                                startLine = regionStart,
                                endLine = index,
                                oursLines = oursLines.toList(),
                                theirsLines = theirsLines.toList()
                            )
                        )
                    }
                    inOurs -> oursLines.add(line)
                    inTheirs -> theirsLines.add(line)
                }
            }

            ConflictFile(
                path = filePath,
                oursContent = extractOursContent(content),
                theirsContent = extractTheirsContent(content),
                baseContent = null,
                conflictMarkers = regions
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractOursContent(content: String): String {
        val result = StringBuilder()
        var inOurs = false
        var inTheirs = false

        content.lines().forEach { line ->
            when {
                line.startsWith("<<<<<<<") -> inOurs = true
                line.startsWith("=======") && inOurs -> {
                    inOurs = false
                    inTheirs = true
                }
                line.startsWith(">>>>>>>") -> inTheirs = false
                inOurs -> result.appendLine(line)
                !inTheirs && !inOurs -> result.appendLine(line)
            }
        }
        return result.toString().trimEnd()
    }

    private fun extractTheirsContent(content: String): String {
        val result = StringBuilder()
        var inOurs = false
        var inTheirs = false

        content.lines().forEach { line ->
            when {
                line.startsWith("<<<<<<<") -> inOurs = true
                line.startsWith("=======") && inOurs -> {
                    inOurs = false
                    inTheirs = true
                }
                line.startsWith(">>>>>>>") -> inTheirs = false
                inTheirs -> result.appendLine(line)
                !inTheirs && !inOurs -> result.appendLine(line)
            }
        }
        return result.toString().trimEnd()
    }

    suspend fun resolveConflict(repo: GitRepo, filePath: String, resolvedContent: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(repo.localPath, filePath)
            file.writeText(resolvedContent)

            Git.open(repo.localPath).use { git ->
                git.add().addFilepattern(filePath).call()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    suspend fun completeMerge(repo: GitRepo, message: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val conflicts = git.status().call().conflicting
                if (conflicts.isNotEmpty()) {
                    return@withContext Result.failure(Exception("There are still unresolved conflicts"))
                }

                val commitMessage = message ?: "Merge commit"
                git.commit().setMessage(commitMessage).call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e.toGitError())
        }
    }

    private companion object {
        /** Statuses that mean the remote accepted the ref, whether or not it moved. */
        val ACCEPTED_PUSH_STATUSES = setOf(
            RemoteRefUpdate.Status.OK,
            RemoteRefUpdate.Status.UP_TO_DATE
        )
    }
}

/** A git operation that completed without throwing, but did not do what was asked. */
class GitOperationException(message: String) : Exception(message)

data class RepoFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

data class CommitChange(
    val path: String,
    val changeType: String
)

/** What a push actually did, so the UI can tell "sent" apart from "nothing to send". */
sealed interface PushOutcome {
    /** At least one ref on the remote advanced. [refs] names them. */
    data class Pushed(val refs: List<String>) : PushOutcome

    /** The remote already had everything; nothing was transferred. */
    data object UpToDate : PushOutcome
}
