package com.example.roboticgit.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class AuthManager(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val prefs = EncryptedSharedPreferences.create(
        "auth_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveGitHubToken(token: String) {
        prefs.edit().putString("github_token", token).apply()
    }

    fun getGitHubToken(): String? {
        return prefs.getString("github_token", null)
    }

    fun saveGiteaConfig(baseUrl: String, token: String) {
        prefs.edit()
            .putString("gitea_base_url", baseUrl)
            .putString("gitea_token", token)
            .apply()
    }

    fun getGiteaBaseUrl(): String? = prefs.getString("gitea_base_url", null)
    fun getGiteaToken(): String? = prefs.getString("gitea_token", null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
