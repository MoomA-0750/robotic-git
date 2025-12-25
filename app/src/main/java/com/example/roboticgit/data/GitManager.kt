package com.example.roboticgit.data

import com.example.roboticgit.data.model.GitRepo
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
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
        token: String? = null
    ): Result<GitRepo> = withContext(Dispatchers.IO) {
        val destination = File(rootDir, name)
        if (destination.exists()) {
             return@withContext Result.failure(Exception("Directory already exists"))
        }

        try {
            val cloneCommand = Git.cloneRepository()
                .setURI(url)
                .setDirectory(destination)
            
            if (!token.isNullOrBlank()) {
                // For GitHub/Gitea tokens, username can be anything (or the token itself)
                // Using "token" as username and the token as password is a common pattern
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
                val commits = git.log().call().toList()
                Result.success(commits)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFormattedStatus(repo: GitRepo): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                val status = git.status().call()
                val sb = StringBuilder()
                if (status.isClean) {
                    sb.append("Clean")
                } else {
                    if (status.added.isNotEmpty()) sb.append("Added: ${status.added}\n")
                    if (status.changed.isNotEmpty()) sb.append("Changed: ${status.changed}\n")
                    if (status.removed.isNotEmpty()) sb.append("Removed: ${status.removed}\n")
                    if (status.missing.isNotEmpty()) sb.append("Missing: ${status.missing}\n")
                    if (status.modified.isNotEmpty()) sb.append("Modified: ${status.modified}\n")
                    if (status.untracked.isNotEmpty()) sb.append("Untracked: ${status.untracked}\n")
                }
                Result.success(sb.toString())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun commit(repo: GitRepo, message: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repo.localPath).use { git ->
                git.add().addFilepattern(".").call() // Auto-add all
                git.commit().setMessage(message).call()
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
