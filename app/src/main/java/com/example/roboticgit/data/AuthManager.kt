package com.example.roboticgit.data

import android.content.Context
import android.os.Environment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.roboticgit.data.model.Account
import com.example.roboticgit.data.model.AppFont
import com.example.roboticgit.data.model.ThemeMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AuthManager(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val prefs = EncryptedSharedPreferences.create(
        "auth_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun getAccounts(): List<Account> {
        val accountsJson = prefs.getString("accounts_list", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Account>>(accountsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAccounts(accounts: List<Account>) {
        val accountsJson = json.encodeToString(accounts)
        prefs.edit().putString("accounts_list", accountsJson).apply()
    }

    fun addAccount(account: Account) {
        val current = getAccounts().toMutableList()
        current.add(account)
        saveAccounts(current)
    }

    fun removeAccount(accountId: String) {
        val current = getAccounts().filter { it.id != accountId }
        saveAccounts(current)
    }

    fun getDefaultCloneDir(): String {
        return prefs.getString("default_clone_dir", File(Environment.getExternalStorageDirectory(), "RoboticGit").absolutePath) ?: ""
    }

    fun setDefaultCloneDir(path: String) {
        prefs.edit().putString("default_clone_dir", path).apply()
    }

    fun getThemeMode(): ThemeMode {
        val mode = prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(mode ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun getDynamicColorEnabled(): Boolean {
        return prefs.getBoolean("dynamic_color_enabled", true)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_color_enabled", enabled).apply()
    }

    fun getAppFont(): AppFont {
        val font = prefs.getString("app_font", AppFont.GOOGLE_SANS_ROUNDED.name)
        return try {
            AppFont.valueOf(font ?: AppFont.GOOGLE_SANS_ROUNDED.name)
        } catch (e: Exception) {
            AppFont.GOOGLE_SANS_ROUNDED
        }
    }

    fun setAppFont(font: AppFont) {
        prefs.edit().putString("app_font", font.name).apply()
    }

    fun getGitUserName(): String = prefs.getString("git_user_name", "") ?: ""
    fun setGitUserName(name: String) = prefs.edit().putString("git_user_name", name).apply()

    fun getGitUserEmail(): String = prefs.getString("git_user_email", "") ?: ""
    fun setGitUserEmail(email: String) = prefs.edit().putString("git_user_email", email).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
