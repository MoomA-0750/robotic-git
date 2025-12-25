package com.example.roboticgit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.roboticgit.data.GitManager
import com.example.roboticgit.data.model.GitRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.jgit.revwalk.RevCommit
import java.io.File

class RepoDetailViewModel(
    private val repo: GitRepo,
    private val gitManager: GitManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<RepoDetailUiState>(RepoDetailUiState.Loading)
    val uiState: StateFlow<RepoDetailUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = RepoDetailUiState.Loading
            val commitsResult = gitManager.getCommits(repo)
            val statusResult = gitManager.getFormattedStatus(repo)

            if (commitsResult.isSuccess && statusResult.isSuccess) {
                _uiState.value = RepoDetailUiState.Success(
                    commits = commitsResult.getOrDefault(emptyList()),
                    status = statusResult.getOrDefault("Unknown")
                )
            } else {
                _uiState.value = RepoDetailUiState.Error(
                    commitsResult.exceptionOrNull()?.message ?: statusResult.exceptionOrNull()?.message ?: "Unknown Error"
                )
            }
        }
    }

    fun commit(message: String) {
        viewModelScope.launch {
            val result = gitManager.commit(repo, message)
            if (result.isSuccess) {
                loadData() // Refresh
            } else {
                // Handle error
            }
        }
    }

    fun push() {
        viewModelScope.launch {
             // In real app, show loading
             gitManager.push(repo)
             // Handle result
        }
    }

     fun pull() {
        viewModelScope.launch {
             // In real app, show loading
             gitManager.pull(repo)
             loadData()
             // Handle result
        }
    }
}

sealed class RepoDetailUiState {
    object Loading : RepoDetailUiState()
    data class Success(
        val commits: List<RevCommit>,
        val status: String
    ) : RepoDetailUiState()
    data class Error(val message: String) : RepoDetailUiState()
}

class RepoDetailViewModelFactory(
    private val rootDir: File,
    private val repoName: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RepoDetailViewModel::class.java)) {
            val repoFile = File(rootDir, repoName)
            val repo = GitRepo(repoName, repoFile.absolutePath, repoFile)
            @Suppress("UNCHECKED_CAST")
            return RepoDetailViewModel(repo, GitManager(rootDir)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
