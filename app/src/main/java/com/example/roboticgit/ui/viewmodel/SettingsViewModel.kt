package com.example.roboticgit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.data.GitHubApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

sealed class ValidationStatus {
    object Idle : ValidationStatus()
    object Loading : ValidationStatus()
    object Success : ValidationStatus()
    data class Error(val message: String) : ValidationStatus()
}

class SettingsViewModel(private val authManager: AuthManager) : ViewModel() {

    private val _githubToken = MutableStateFlow(authManager.getGitHubToken() ?: "")
    val githubToken: StateFlow<String> = _githubToken.asStateFlow()

    private val _giteaBaseUrl = MutableStateFlow(authManager.getGiteaBaseUrl() ?: "")
    val giteaBaseUrl: StateFlow<String> = _giteaBaseUrl.asStateFlow()

    private val _giteaToken = MutableStateFlow(authManager.getGiteaToken() ?: "")
    val giteaToken: StateFlow<String> = _giteaToken.asStateFlow()

    private val _validationStatus = MutableStateFlow<ValidationStatus>(ValidationStatus.Idle)
    val validationStatus: StateFlow<ValidationStatus> = _validationStatus.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun onGitHubTokenChange(newToken: String) {
        _githubToken.value = newToken
        _validationStatus.value = ValidationStatus.Idle
    }

    fun onGiteaConfigChange(baseUrl: String, token: String) {
        _giteaBaseUrl.value = baseUrl
        _giteaToken.value = token
        _validationStatus.value = ValidationStatus.Idle
    }

    fun saveAndVerifyGitHub() {
        val token = _githubToken.value
        if (token.isBlank()) {
            _validationStatus.value = ValidationStatus.Error("Token cannot be empty")
            return
        }

        viewModelScope.launch {
            _validationStatus.value = ValidationStatus.Loading
            try {
                val client = OkHttpClient.Builder().build()
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://api.github.com/")
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()

                val service = retrofit.create(GitHubApiService::class.java)
                // Test the token by fetching user repos (minimal call)
                service.getUserRepos("Bearer $token", perPage = 1)
                
                // If success, save it
                authManager.saveGitHubToken(token)
                _validationStatus.value = ValidationStatus.Success
            } catch (e: Exception) {
                _validationStatus.value = ValidationStatus.Error(e.message ?: "Invalid token or network error")
            }
        }
    }
}

class SettingsViewModelFactory(private val authManager: AuthManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
