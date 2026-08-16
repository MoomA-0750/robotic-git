package com.example.roboticgit.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.data.GitHubApiService
import com.example.roboticgit.data.model.Account
import com.example.roboticgit.data.model.AccountType
import com.example.roboticgit.data.model.AppFont
import com.example.roboticgit.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.MessageDigest

sealed class ValidationStatus {
    object Idle : ValidationStatus()
    object Loading : ValidationStatus()
    object Success : ValidationStatus()
    data class Error(val message: String) : ValidationStatus()
}

class SettingsViewModel(private val authManager: AuthManager) : ViewModel() {

    private val _accounts = MutableStateFlow(authManager.getAccounts())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _defaultCloneDir = MutableStateFlow(authManager.getDefaultCloneDir())
    val defaultCloneDir: StateFlow<String> = _defaultCloneDir.asStateFlow()

    private val _themeMode = MutableStateFlow(authManager.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(authManager.getDynamicColorEnabled())
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    private val _appFont = MutableStateFlow(authManager.getAppFont())
    val appFont: StateFlow<AppFont> = _appFont.asStateFlow()

    private val _gitUserName = MutableStateFlow(authManager.getGitUserName())
    val gitUserName: StateFlow<String> = _gitUserName.asStateFlow()

    private val _gitUserEmail = MutableStateFlow(authManager.getGitUserEmail())
    val gitUserEmail: StateFlow<String> = _gitUserEmail.asStateFlow()

    private val _editorFontSize = MutableStateFlow(authManager.getEditorFontSize())
    val editorFontSize: StateFlow<Int> = _editorFontSize.asStateFlow()

    private val _validationStatus = MutableStateFlow<ValidationStatus>(ValidationStatus.Idle)
    val validationStatus: StateFlow<ValidationStatus> = _validationStatus.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    
    private val service = retrofit.create(GitHubApiService::class.java)

    fun onDefaultCloneDirChange(newPath: String) {
        _defaultCloneDir.value = newPath
        authManager.setDefaultCloneDir(newPath)
    }

    fun onThemeModeChange(newMode: ThemeMode) {
        _themeMode.value = newMode
        authManager.setThemeMode(newMode)
    }

    fun onDynamicColorChange(enabled: Boolean) {
        _dynamicColorEnabled.value = enabled
        authManager.setDynamicColorEnabled(enabled)
    }

    fun onAppFontChange(newFont: AppFont) {
        _appFont.value = newFont
        authManager.setAppFont(newFont)
    }

    fun onGitUserNameChange(newName: String) {
        _gitUserName.value = newName
        authManager.setGitUserName(newName)
    }

    fun onGitUserEmailChange(newEmail: String) {
        _gitUserEmail.value = newEmail
        authManager.setGitUserEmail(newEmail)
    }

    fun onEditorFontSizeChange(newSize: Int) {
        _editorFontSize.value = newSize
        authManager.setEditorFontSize(newSize)
    }

    fun getGravatarUrl(email: String): String {
        val address = email.trim().lowercase()
        if (address.isBlank()) return ""
        val md5 = MessageDigest.getInstance("MD5").digest(address.toByteArray())
        val hash = md5.joinToString("") { "%02x".format(it) }
        return "https://www.gravatar.com/avatar/$hash?d=identicon"
    }

    fun addGitHubAccountManual(token: String) {
        viewModelScope.launch {
            _validationStatus.value = ValidationStatus.Loading
            try {
                val user = service.getUser("Bearer $token")
                val account = Account(
                    name = user.login, 
                    type = AccountType.GITHUB, 
                    token = token,
                    avatarUrl = user.avatarUrl
                )
                authManager.addAccount(account)
                _accounts.value = authManager.getAccounts()
                _validationStatus.value = ValidationStatus.Success
            } catch (e: Exception) {
                _validationStatus.value = ValidationStatus.Error(e.message ?: "Invalid token")
            }
        }
    }

    fun addAccountGeneric(name: String, url: String, token: String, type: AccountType) {
        viewModelScope.launch {
            _validationStatus.value = ValidationStatus.Loading
            try {
                // For Gitea/GitLab/Custom, we'll skip validation for now and just add it
                // You might want to add API checks later
                val account = Account(
                    name = name,
                    type = type,
                    token = token,
                    baseUrl = url
                )
                authManager.addAccount(account)
                _accounts.value = authManager.getAccounts()
                _validationStatus.value = ValidationStatus.Success
            } catch (e: Exception) {
                _validationStatus.value = ValidationStatus.Error(e.message ?: "Failed to add account")
            }
        }
    }

    fun removeAccount(id: String) {
        authManager.removeAccount(id)
        _accounts.value = authManager.getAccounts()
    }
    
    fun resetValidationStatus() {
        _validationStatus.value = ValidationStatus.Idle
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
