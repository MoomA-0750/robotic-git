package com.example.roboticgit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.data.CommitChange
import com.example.roboticgit.data.FileAccess
import com.example.roboticgit.data.FileContents
import com.example.roboticgit.data.GitError
import com.example.roboticgit.data.GitManager
import com.example.roboticgit.data.RemoteHost
import com.example.roboticgit.data.forRemote
import com.example.roboticgit.data.PushOutcome
import com.example.roboticgit.data.RepoFile
import com.example.roboticgit.data.model.Account
import com.example.roboticgit.data.model.BranchInfo
import com.example.roboticgit.data.model.ConflictFile
import com.example.roboticgit.data.model.CommitSummary
import com.example.roboticgit.data.model.FileStatus
import com.example.roboticgit.data.model.GitRepo
import com.example.roboticgit.data.model.MergeResult
import com.example.roboticgit.data.model.MergeStatus
import com.example.roboticgit.data.model.RemoteInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class RepoDetailViewModel(
    private val repo: GitRepo,
    private val gitManager: GitManager,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<RepoDetailUiState>(RepoDetailUiState.Loading)
    val uiState: StateFlow<RepoDetailUiState> = _uiState.asStateFlow()

    /** Genuine failures. Surfaced as a modal dialog. */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Confirmations of things that worked. Kept apart from [errorMessage] so a
     * successful push stops being announced in a dialog titled "Error".
     */
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /** True while a refresh runs over content that is already on screen. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isMerging = MutableStateFlow(false)
    val isMerging: StateFlow<Boolean> = _isMerging.asStateFlow()

    private val _mergeResult = MutableStateFlow<MergeResult?>(null)
    val mergeResult: StateFlow<MergeResult?> = _mergeResult.asStateFlow()

    private val _remotes = MutableStateFlow<List<RemoteInfo>>(emptyList())
    val remotes: StateFlow<List<RemoteInfo>> = _remotes.asStateFlow()

    private val _conflictingFiles = MutableStateFlow<List<String>>(emptyList())
    val conflictingFiles: StateFlow<List<String>> = _conflictingFiles.asStateFlow()

    init {
        viewModelScope.launch {
            // Also done when a repository is first added, but repositories added
            // before that existed -- or cloned on a machine whose filesystem could
            // store the executable bit -- would otherwise keep showing phantom
            // modifications forever. It only writes when the value is wrong.
            gitManager.alignConfigWithFilesystem(repo.localPath)
            loadData()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            // Only blank the screen out when there is nothing to show yet. Every
            // staging toggle calls this, and swapping the whole pane for a spinner
            // on each one is most of why the app feels heavy.
            val hasContent = _uiState.value is RepoDetailUiState.Success
            if (hasContent) {
                _isRefreshing.value = true
            } else {
                _uiState.value = RepoDetailUiState.Loading
            }

            // One call, one Git.open. This used to be seven separate calls, each
            // opening and parsing the repository again.
            gitManager.loadSnapshot(repo, COMMIT_HISTORY_LIMIT)
                .onSuccess { snapshot ->
                    _isMerging.value = snapshot.isMerging
                    _conflictingFiles.value = snapshot.conflictingFiles
                    _remotes.value = snapshot.remotes
                    _uiState.value = RepoDetailUiState.Success(
                        commits = snapshot.commits,
                        fileStatuses = snapshot.fileStatuses,
                        currentBranch = snapshot.currentBranch,
                        branches = snapshot.branches,
                        hasMoreCommits = snapshot.hasMoreCommits
                    )
                }
                .onFailure { error ->
                    _uiState.value = RepoDetailUiState.Error(
                        error.message ?: "Could not read this repository."
                    )
                }

            _isRefreshing.value = false
        }
    }

    suspend fun getFileDiff(fileStatus: FileStatus): String {
        val result = gitManager.getFileDiff(repo, fileStatus)
        return result.getOrElse { "Error: ${it.message}" }
    }

    suspend fun readFile(path: String): FileContents {
        return gitManager.readFile(repo, path).getOrElse {
            FileContents(text = "", sizeBytes = 0, access = FileAccess.EDITABLE)
        }
    }

    suspend fun listFiles(relativePath: String): List<RepoFile> {
        return gitManager.listFiles(repo, relativePath)
    }

    suspend fun getCommitChanges(commitId: String): List<CommitChange> {
        return gitManager.getCommitChanges(repo, commitId)
    }

    suspend fun getCommitFileDiff(commitId: String, path: String): String {
        return gitManager.getCommitFileDiff(repo, commitId, path)
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

    /**
     * The stored account for this repository's remote.
     *
     * Credentials used to be taken from whichever account was first in the list,
     * so a Gitea repository was pushed with a GitHub token. Matching on the
     * remote's host sends each token only to the host it was issued for.
     */
    private suspend fun accountForRemote(): Account? {
        val remote = _remotes.value.firstOrNull { it.name == "origin" } ?: _remotes.value.firstOrNull()
        // getAccounts() unlocks the Keystore on first use; keep it off the main thread.
        val accounts = withContext(Dispatchers.IO) { authManager.getAccounts() }
        return accounts.forRemote(remote?.pushUrl)
    }

    /** Names the host when nothing is stored for it, so the user knows what to add. */
    private fun missingAccountHint(): String? {
        val remote = _remotes.value.firstOrNull { it.name == "origin" } ?: _remotes.value.firstOrNull()
        val host = RemoteHost.of(remote?.pushUrl) ?: return null
        return "No account is stored for $host. Add one in Settings › Accounts."
    }

    fun push() {
        viewModelScope.launch {
            val token = accountForRemote()?.token
            gitManager.push(repo, token)
                .onSuccess { outcome ->
                    _statusMessage.value = when (outcome) {
                        is PushOutcome.Pushed ->
                            "Pushed ${outcome.refs.joinToString(", ").ifBlank { "changes" }}"
                        PushOutcome.UpToDate -> "Everything up to date"
                    }
                    loadData()
                }
                .onFailure { _errorMessage.value = describeAuthAwareFailure("Push failed", it) }
        }
    }

    fun pull() {
        viewModelScope.launch {
            val token = accountForRemote()?.token
            gitManager.pull(repo, token)
                .onSuccess {
                    _statusMessage.value = "Pull complete"
                    loadData()
                }
                .onFailure { _errorMessage.value = describeAuthAwareFailure("Pull failed", it) }
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

    /**
     * Adds the missing-account hint when a network operation failed on
     * authentication, since "check your token" is not actionable if the user
     * never had an account for that host to begin with.
     */
    private suspend fun describeAuthAwareFailure(prefix: String, error: Throwable): String {
        val base = "$prefix: ${error.message}"
        if (error !is GitError.AuthenticationFailed) return base
        val hint = missingAccountHint()?.takeIf { accountForRemote() == null } ?: return base
        return "$base\n\n$hint"
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
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

    companion object {
        /**
         * How many commits the History tab loads. Reading the whole log meant an
         * unbounded list of RevCommit objects for any repository with real
         * history, on every refresh.
         */
        const val COMMIT_HISTORY_LIMIT = 200
    }
}

sealed class RepoDetailUiState {
    object Loading : RepoDetailUiState()
    data class Success(
        val commits: List<CommitSummary>,
        val fileStatuses: List<FileStatus>,
        val currentBranch: String? = null,
        val branches: List<BranchInfo> = emptyList(),
        /** True when the log was cut off at [RepoDetailViewModel.COMMIT_HISTORY_LIMIT]. */
        val hasMoreCommits: Boolean = false
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
            val repoFile = resolveRepoDirectory()
            val repo = GitRepo(repoName, repoFile.absolutePath, repoFile)
            @Suppress("UNCHECKED_CAST")
            return RepoDetailViewModel(repo, GitManager(rootDir), authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

    /**
     * Navigation carries only the repository's name, but repositories added by
     * path live outside the clone directory. Assuming `rootDir/repoName` opened
     * the wrong directory -- or nothing at all -- for every such repository.
     */
    private fun resolveRepoDirectory(): File {
        val insideRoot = File(rootDir, repoName)
        if (File(insideRoot, ".git").exists()) return insideRoot

        return authManager.getTrackedRepoPaths()
            .map(::File)
            .firstOrNull { it.name == repoName && File(it, ".git").exists() }
            ?: insideRoot
    }
}
