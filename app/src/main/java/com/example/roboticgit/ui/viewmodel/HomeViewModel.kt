package com.example.roboticgit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.data.GitHubApiService
import com.example.roboticgit.data.GitManager
import com.example.roboticgit.data.model.Account
import com.example.roboticgit.data.model.GitRepo
import com.example.roboticgit.data.model.RemoteRepo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File

class HomeViewModel(
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _repos = MutableStateFlow<List<GitRepo>>(emptyList())
    val repos: StateFlow<List<GitRepo>> = _repos.asStateFlow()

    private val _remoteRepos = MutableStateFlow<List<RemoteRepo>>(emptyList())
    val remoteRepos: StateFlow<List<RemoteRepo>> = _remoteRepos.asStateFlow()

    private val _accounts = MutableStateFlow<List<Account>>(authManager.getAccounts())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<Account?>(null)
    val selectedAccount: StateFlow<Account?> = _selectedAccount.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedRepos = MutableStateFlow<Set<String>>(emptySet())
    val selectedRepos: StateFlow<Set<String>> = _selectedRepos.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    
    private fun getGitManager(): GitManager {
        return GitManager(File(authManager.getDefaultCloneDir()))
    }

    init {
        loadRepositories()
        _selectedAccount.value = _accounts.value.firstOrNull()
        fetchRemoteRepositories()
    }

    fun toggleRepoSelection(repoName: String) {
        _selectedRepos.update { current ->
            if (current.contains(repoName)) {
                current - repoName
            } else {
                current + repoName
            }
        }
    }

    fun clearSelection() {
        _selectedRepos.value = emptySet()
    }

    fun refreshAccounts() {
        val list = authManager.getAccounts()
        _accounts.value = list
        if (_selectedAccount.value == null || !list.any { it.id == _selectedAccount.value?.id }) {
            _selectedAccount.value = list.firstOrNull()
        }
    }

    fun selectAccount(account: Account) {
        _selectedAccount.value = account
        fetchRemoteRepositories()
    }

    fun loadRepositories() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val repoList = getGitManager().listRepositories()
                _repos.value = repoList
                _uiState.value = HomeUiState.Success(repoList)
            } catch (e: Exception) {
                 _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun refreshAllRepositories() {
        refreshSelectedRepositories(_repos.value.map { it.name }.toSet())
    }

    fun refreshSelectedRepositories(repoNames: Set<String>) {
        val account = _selectedAccount.value
        val token = account?.token
        val gitManager = getGitManager()

        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val reposToFetch = _repos.value.filter { repoNames.contains(it.name) }
                val fetchTasks = reposToFetch.map { repo ->
                    async {
                        gitManager.fetch(repo, token)
                    }
                }
                fetchTasks.awaitAll()
                loadRepositories()
                if (repoNames.size < _repos.value.size) {
                    clearSelection()
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun pullSelectedRepositories() {
        val repoNames = _selectedRepos.value
        val account = _selectedAccount.value
        val token = account?.token
        val gitManager = getGitManager()

        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val reposToPull = _repos.value.filter { repoNames.contains(it.name) }
                val pullTasks = reposToPull.map { repo ->
                    async {
                        gitManager.pull(repo, token)
                    }
                }
                pullTasks.awaitAll()
                loadRepositories()
                clearSelection()
            } catch (e: Exception) {
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun deleteSelectedRepositories() {
        val repoNames = _selectedRepos.value
        viewModelScope.launch {
            try {
                val reposToDelete = _repos.value.filter { repoNames.contains(it.name) }
                reposToDelete.forEach { repo ->
                    repo.localPath.deleteRecursively()
                }
                loadRepositories()
                clearSelection()
            } catch (e: Exception) {
            }
        }
    }

    fun fetchRemoteRepositories() {
        val account = _selectedAccount.value ?: return
        
        viewModelScope.launch {
            try {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                val client = OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(account.baseUrl ?: "https://api.github.com/")
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()

                val service = retrofit.create(GitHubApiService::class.java)
                val response = service.getUserRepos("Bearer ${account.token}")
                _remoteRepos.value = response
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun cloneRepository(url: String, name: String) {
        val account = _selectedAccount.value
        val token = account?.token
        
        viewModelScope.launch {
             val placeholder = GitRepo(
                 name = name,
                 path = "",
                 localPath = File(""),
                 isCloning = true,
                 statusMessage = "Starting..."
             )
             _repos.update { current -> listOf(placeholder) + current }

             val result = getGitManager().cloneRepository(url, name, token) { task, progress ->
                 _repos.update { current ->
                     current.map { 
                         if (it.name == name && it.isCloning) {
                             it.copy(progress = progress, statusMessage = task)
                         } else it
                     }
                 }
             }

             if (result.isSuccess) {
                 loadRepositories()
             } else {
                 _repos.update { current -> current.filterNot { it.name == name && it.isCloning } }
             }
        }
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val repos: List<GitRepo>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModelFactory(
    private val authManager: AuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
