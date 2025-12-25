package com.example.roboticgit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.data.GitHubApiService
import com.example.roboticgit.data.GitManager
import com.example.roboticgit.data.model.GitRepo
import com.example.roboticgit.data.model.RemoteRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File

class HomeViewModel(
    private val gitManager: GitManager,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _repos = MutableStateFlow<List<GitRepo>>(emptyList())
    val repos: StateFlow<List<GitRepo>> = _repos.asStateFlow()

    private val _remoteRepos = MutableStateFlow<List<RemoteRepo>>(emptyList())
    val remoteRepos: StateFlow<List<RemoteRepo>> = _remoteRepos.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadRepositories()
    }

    fun loadRepositories() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val repoList = gitManager.listRepositories()
                _repos.value = repoList
                _uiState.value = HomeUiState.Success(repoList)
            } catch (e: Exception) {
                 _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun fetchRemoteRepositories() {
        val token = authManager.getGitHubToken() ?: return
        
        viewModelScope.launch {
            try {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                val client = OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl("https://api.github.com/")
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()

                val service = retrofit.create(GitHubApiService::class.java)
                val response = service.getUserRepos("Bearer $token")
                _remoteRepos.value = response
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun cloneRepository(url: String, name: String) {
        val token = authManager.getGitHubToken()
        viewModelScope.launch {
             val result = gitManager.cloneRepository(url, name, token)
             if (result.isSuccess) {
                 loadRepositories()
             } else {
                 // Handle error
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
    private val rootDir: File,
    private val authManager: AuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(GitManager(rootDir), authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
