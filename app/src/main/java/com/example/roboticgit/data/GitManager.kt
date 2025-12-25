package com.example.roboticgit.data

import com.example.roboticgit.data.model.FileState
import com.example.roboticgit.data.model.FileStatus
import com.example.roboticgit.data.model.GitRepo
import org.eclipse.jgit.api.Git
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
                repos.add(GitRepo(file.name, file.absolutePath, file))
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

            cloneCommand.call().close()
            Result.success(GitRepo(name, destination.absolutePath, destination))
        } catch (e: GitAPIException) {
            Result.failure(e)
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
                
                status.modified.forEach { result.add(FileStatus(it, FileState.MODIFIED, false)) }
                status.untracked.forEach { result.add(FileStatus(it, FileState.UNTRACKED, false)) }
                status.missing.forEach { result.add(FileStatus(it, FileState.MISSING, false)) }
                
                status.changed.forEach { result.add(FileStatus(it, FileState.MODIFIED, true)) }
                status.added.forEach { result.add(FileStatus(it, FileState.ADDED, true)) }
                status.removed.forEach { result.add(FileStatus(it, FileState.REMOVED, true)) }
                
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
