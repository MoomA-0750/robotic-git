package com.example.roboticgit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.data.CommitChange
import com.example.roboticgit.data.GitManager
import com.example.roboticgit.data.RepoFile
import com.example.roboticgit.data.model.BranchInfo
import com.example.roboticgit.data.model.ConflictFile
import com.example.roboticgit.data.model.FileStatus
import com.example.roboticgit.data.model.GitRepo
import com.example.roboticgit.data.model.MergeResult
import com.example.roboticgit.data.model.MergeStatus
import com.example.roboticgit.data.model.RemoteInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.jgit.revwalk.RevCommit
import java.io.File
import java.security.MessageDigest

class RepoDetailViewModel(
    private val repo: GitRepo,
    private val gitManager: GitManager,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<RepoDetailUiState>(RepoDetailUiState.Loading)
    val uiState: StateFlow<RepoDetailUiState> = _uiState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isMerging = MutableStateFlow(false)
    val isMerging: StateFlow<Boolean> = _isMerging.asStateFlow()

    private val _mergeResult = MutableStateFlow<MergeResult?>(null)
    val mergeResult: StateFlow<MergeResult?> = _mergeResult.asStateFlow()

    private val _remotes = MutableStateFlow<List<RemoteInfo>>(emptyList())
    val remotes: StateFlow<List<RemoteInfo>> = _remotes.asStateFlow()

    private val _conflictingFiles = MutableStateFlow<List<String>>(emptyList())
    val conflictingFiles: StateFlow<List<String>> = _conflictingFiles.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = RepoDetailUiState.Loading
            val commitsResult = gitManager.getCommits(repo)
            val fileStatusesResult = gitManager.getFileStatuses(repo)
            val currentBranch = gitManager.getCurrentBranch(repo)
            val branchesResult = gitManager.listBranches(repo)

            // Check merge state
            _isMerging.value = gitManager.isMerging(repo)
            _conflictingFiles.value = gitManager.getConflictingFiles(repo)

            // Load remotes
            gitManager.listRemotes(repo).onSuccess { _remotes.value = it }

            if (commitsResult.isSuccess && fileStatusesResult.isSuccess) {
                _uiState.value = RepoDetailUiState.Success(
                    commits = commitsResult.getOrDefault(emptyList()),
                    fileStatuses = fileStatusesResult.getOrDefault(emptyList()),
                    currentBranch = currentBranch,
                    branches = branchesResult.getOrDefault(emptyList())
                )
            } else {
                _uiState.value = RepoDetailUiState.Error(
                    commitsResult.exceptionOrNull()?.message ?: fileStatusesResult.exceptionOrNull()?.message ?: "Unknown Error"
                )
            }
        }
    }

    suspend fun getFileDiff(fileStatus: FileStatus): String {
        val result = gitManager.getFileDiff(repo, fileStatus)
        return result.getOrElse { "Error: ${it.message}" }
    }

    suspend fun readFile(path: String): String {
        return gitManager.readFile(repo, path).getOrElse { "" }
    }

    suspend fun listFiles(relativePath: String): List<RepoFile> {
        return gitManager.listFiles(repo, relativePath)
    }

    suspend fun getCommitChanges(commit: RevCommit): List<CommitChange> {
        return gitManager.getCommitChanges(repo, commit)
    }

    suspend fun getCommitFileDiff(commit: RevCommit, path: String): String {
        return gitManager.getCommitFileDiff(repo, commit, path)
    }

    fun saveFile(path: String, content: String) {
        viewModelScope.launch {
            gitManager.writeFile(repo, path, content)
            loadData()
        }
    }

    fun toggleStage(fileStatus: FileStatus) {
        viewModelScope.launch {
            if (fileStatus.isStaged) {
                gitManager.unstageFile(repo, fileStatus.path)
            } else {
                gitManager.stageFile(repo, fileStatus.path)
            }
            loadData()
        }
    }

    fun rollbackFile(fileStatus: FileStatus) {
        viewModelScope.launch {
            gitManager.rollbackFile(repo, fileStatus.path)
            loadData()
        }
    }

    fun getGravatarUrl(email: String): String {
        val address = email.trim().lowercase()
        if (address.isBlank()) return ""
        val md5 = MessageDigest.getInstance("MD5").digest(address.toByteArray())
        val hash = md5.joinToString("") { "%02x".format(it) }
        return "https://www.gravatar.com/avatar/$hash?d=identicon"
    }

    fun commit(message: String) {
        viewModelScope.launch {
            val name = authManager.getGitUserName()
            val email = authManager.getGitUserEmail()
            val result = gitManager.commit(repo, message, name, email)
            if (result.isSuccess) {
                loadData() // Refresh
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun push() {
        viewModelScope.launch {
             gitManager.push(repo)
        }
    }

     fun pull() {
        viewModelScope.launch {
             gitManager.pull(repo)
             loadData()
        }
    }

    fun createBranch(name: String) {
        viewModelScope.launch {
            val result = gitManager.createBranch(repo, name)
            if (result.isSuccess) {
                loadData()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun checkoutBranch(branchName: String, force: Boolean = false) {
        viewModelScope.launch {
            if (!force && gitManager.hasUncommittedChanges(repo)) {
                _errorMessage.value = "UNCOMMITTED_CHANGES"
                return@launch
            }
            
            val result = gitManager.checkoutBranch(repo, branchName)
            if (result.isSuccess) {
                loadData()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun deleteBranch(branchName: String, force: Boolean = false) {
        viewModelScope.launch {
            val result = gitManager.deleteBranch(repo, branchName, force)
            if (result.isSuccess) {
                loadData()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ========== Merge functionality ==========

    fun mergeBranch(branchName: String, fastForwardOnly: Boolean = false, message: String? = null) {
        viewModelScope.launch {
            val result = gitManager.mergeBranch(repo, branchName, fastForwardOnly, message)
            _mergeResult.value = result

            if (result.status == MergeStatus.CONFLICTING) {
                _isMerging.value = true
                _conflictingFiles.value = result.conflictingFiles
            }

            loadData()
        }
    }

    fun abortMerge() {
        viewModelScope.launch {
            val result = gitManager.abortMerge(repo)
            if (result.isSuccess) {
                _isMerging.value = false
                _mergeResult.value = null
                _conflictingFiles.value = emptyList()
                loadData()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun completeMerge(message: String? = null) {
        viewModelScope.launch {
            val result = gitManager.completeMerge(repo, message)
            if (result.isSuccess) {
                _isMerging.value = false
                _mergeResult.value = null
                _conflictingFiles.value = emptyList()
                loadData()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun clearMergeResult() {
        _mergeResult.value = null
    }

    // ========== Remote management ==========

    fun addRemote(name: String, url: String) {
        viewModelScope.launch {
            val result = gitManager.addRemote(repo, name, url)
            if (result.isSuccess) {
                gitManager.listRemotes(repo).onSuccess { _remotes.value = it }
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun removeRemote(name: String) {
        viewModelScope.launch {
            val result = gitManager.removeRemote(repo, name)
            if (result.isSuccess) {
                gitManager.listRemotes(repo).onSuccess { _remotes.value = it }
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun updateRemoteUrl(name: String, url: String) {
        viewModelScope.launch {
            val result = gitManager.setRemoteUrl(repo, name, url)
            if (result.isSuccess) {
                gitManager.listRemotes(repo).onSuccess { _remotes.value = it }
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }

    // ========== Conflict resolution ==========

    suspend fun getConflictContent(filePath: String): ConflictFile? {
        return gitManager.getConflictContent(repo, filePath)
    }

    fun resolveConflict(filePath: String, resolvedContent: String) {
        viewModelScope.launch {
            val result = gitManager.resolveConflict(repo, filePath, resolvedContent)
            if (result.isSuccess) {
                _conflictingFiles.value = gitManager.getConflictingFiles(repo)
                loadData()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
            }
        }
    }
}

sealed class RepoDetailUiState {
    object Loading : RepoDetailUiState()
    data class Success(
        val commits: List<RevCommit>,
        val fileStatuses: List<FileStatus>,
        val currentBranch: String? = null,
        val branches: List<BranchInfo> = emptyList()
    ) : RepoDetailUiState()
    data class Error(val message: String) : RepoDetailUiState()
}

class RepoDetailViewModelFactory(
    private val authManager: AuthManager,
    private val rootDir: File,
    private val repoName: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RepoDetailViewModel::class.java)) {
            val repoFile = File(rootDir, repoName)
            val repo = GitRepo(repoName, repoFile.absolutePath, repoFile)
            @Suppress("UNCHECKED_CAST")
            return RepoDetailViewModel(repo, GitManager(rootDir), authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
