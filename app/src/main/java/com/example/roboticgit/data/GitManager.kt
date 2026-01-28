package com.example.roboticgit.data

import com.example.roboticgit.data.model.BranchInfo
import com.example.roboticgit.data.model.ConflictFile
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
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
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

    suspend fun listRepositories(): List<GitRepo> = withContext(Dispatchers.IO) {
        val repos = mutableListOf<GitRepo>()
        rootDir.listFiles()?.forEach { file ->
            if (file.isDirectory && File(file, ".git").exists()) {
                val lastCommitTime = try {
                    Git.open(file).use { git ->
                        val head = git.repository.resolve(Constants.HEAD)
                        if (head != null) {
                            val revWalk = RevWalk(git.repository)
                            val commit = revWalk.parseCommit(head)
                            commit.commitTime * 1000L // convert to milliseconds
                        } else 0L
                    }
                } catch (e: Exception) {
                    0L
                }
                repos.add(GitRepo(file.name, file.absolutePath, file, lastCommitTime = lastCommitTime))
            }
        }
        repos
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
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
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
            Result.failure(e)
        }
    }

    suspend fun getCommits(repo: GitRepo): Result<List<RevCommit>> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                if (git.repository.resolve(Constants.HEAD) == null) {
                    return@withContext Result.success(emptyList())
                }
                val commits = git.log().call().toList()
                Result.success(commits)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileStatuses(repo: GitRepo): Result<List<FileStatus>> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val status = git.status().call()
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

                Result.success(result.sortedBy { it.path })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
            Result.failure(e)
        }
    }

    suspend fun getCommitChanges(repo: GitRepo, commit: RevCommit): List<CommitChange> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val repoObj = git.repository
                val reader = repoObj.newObjectReader()
                val oldTreeIter = CanonicalTreeParser()
                if (commit.parentCount > 0) {
                    oldTreeIter.reset(reader, commit.getParent(0).tree)
                } else {
                    // For the first commit, compare against empty tree
                    oldTreeIter.reset()
                }
                val newTreeIter = CanonicalTreeParser()
                newTreeIter.reset(reader, commit.tree)

                DiffFormatter(null).use { formatter ->
                    formatter.setRepository(repoObj)
                    val diffs = formatter.scan(oldTreeIter, newTreeIter)
                    diffs.map { entry ->
                        CommitChange(
                            path = if (entry.changeType == DiffEntry.ChangeType.DELETE) entry.oldPath else entry.newPath,
                            changeType = entry.changeType.name
                        )
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCommitFileDiff(repo: GitRepo, commit: RevCommit, path: String): String = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val repoObj = git.repository
                val out = ByteArrayOutputStream()
                DiffFormatter(out).use { formatter ->
                    formatter.setRepository(repoObj)
                    formatter.setPathFilter(PathFilter.create(path))
                    
                    val reader = repoObj.newObjectReader()
                    val oldTreeIter = CanonicalTreeParser()
                    if (commit.parentCount > 0) {
                        oldTreeIter.reset(reader, commit.getParent(0).tree)
                    } else {
                        oldTreeIter.reset()
                    }
                    val newTreeIter = CanonicalTreeParser()
                    newTreeIter.reset(reader, commit.tree)
                    
                    val diffs = formatter.scan(oldTreeIter, newTreeIter)
                    for (entry in diffs) {
                        formatter.format(entry)
                    }
                    out.toString("UTF-8")
                }
            }
        } catch (e: Exception) {
            "Error loading diff: ${e.message}"
        }
    }

    suspend fun readFile(repo: GitRepo, path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(repo.localPath, path)
            if (file.exists()) {
                Result.success(file.readText())
            } else {
                Result.failure(Exception("File not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeFile(repo: GitRepo, path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(repo.localPath, path)
            file.writeText(content)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
            Result.failure(e)
        }
    }

    suspend fun unstageFile(repo: GitRepo, path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.reset().addPath(path).call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rollbackFile(repo: GitRepo, path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.checkout().addPath(path).call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
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
                git.add().addFilepattern(".").call() // Auto-add all
                val commitCommand = git.commit().setMessage(message)
                
                if (!authorName.isNullOrBlank() && !authorEmail.isNullOrBlank()) {
                    commitCommand.setAuthor(authorName, authorEmail)
                    commitCommand.setCommitter(authorName, authorEmail)
                }
                
                commitCommand.call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun push(repo: GitRepo, token: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val pushCommand = git.push()
                if (!token.isNullOrBlank()) {
                    pushCommand.setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
                }
                pushCommand.call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pull(repo: GitRepo, token: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val pullCommand = git.pull()
                if (!token.isNullOrBlank()) {
                    pullCommand.setCredentialsProvider(UsernamePasswordCredentialsProvider("token", token))
                }
                pullCommand.call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
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
                val currentBranch = git.repository.branch
                
                val localBranches = git.branchList().call()
                val remoteBranches = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()
                
                val revWalk = RevWalk(git.repository)
                
                val result = (localBranches.map { it to false } + remoteBranches.map { it to true }).map { (ref, isRemote) ->
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
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
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
            Result.failure(e)
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
            Result.failure(e)
        }
    }

    suspend fun checkoutBranch(repo: GitRepo, branchName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.checkout().setName(branchName).call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
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
            Result.failure(e)
        }
    }

    // ========== Task 6: Remote management ==========

    suspend fun listRemotes(repo: GitRepo): Result<List<RemoteInfo>> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val config = git.repository.config
                val remoteNames = config.getSubsections("remote")

                val remotes = remoteNames.map { name ->
                    RemoteInfo(
                        name = name,
                        fetchUrl = config.getString("remote", name, "url") ?: "",
                        pushUrl = config.getString("remote", name, "pushurl")
                            ?: config.getString("remote", name, "url") ?: ""
                    )
                }
                Result.success(remotes)
            }
        } catch (e: Exception) {
            Result.failure(e)
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
            Result.failure(e)
        }
    }

    suspend fun removeRemote(repo: GitRepo, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.remoteRemove().setRemoteName(name).call()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
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
            Result.failure(e)
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
            Result.failure(e)
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
            Result.failure(e)
        }
    }
}

data class RepoFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

data class CommitChange(
    val path: String,
    val changeType: String
)
