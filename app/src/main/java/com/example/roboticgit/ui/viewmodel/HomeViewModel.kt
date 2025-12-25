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

    private val json = Json { ignoreUnknownKeys = true }
    
    // Dynamic GitManager based on current settings
    private fun getGitManager(): GitManager {
        return GitManager(File(authManager.getDefaultCloneDir()))
    }

    init {
        loadRepositories()
        _selectedAccount.value = _accounts.value.firstOrNull()
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
             // Add a placeholder for the cloning repo
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
                 // Remove placeholder on failure
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
