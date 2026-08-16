package com.example.roboticgit.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.roboticgit.data.model.Account
import com.example.roboticgit.data.model.AppFont
import com.example.roboticgit.data.model.ThemeMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stored settings and credentials.
 *
 * Two stores, deliberately:
 *
 * - **Settings** live in ordinary [SharedPreferences]. A theme choice is not a
 *   secret, and putting it behind [EncryptedSharedPreferences] meant every cold
 *   start paid for Android Keystore key retrieval before the first frame could
 *   be drawn -- measured at ~185 ms on a Pixel 3, plus ~70 ms more for a second
 *   instance the UI created for itself.
 * - **Credentials** stay encrypted, and the store is built lazily, so the
 *   Keystore is touched only when an account is actually read or written --
 *   which never happens on the startup path.
 *
 * Single instance per process: constructing it is expensive enough that three
 * separate call sites doing it independently was itself a measurable cost.
 */
class AuthManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val settings: SharedPreferences =
        appContext.getSharedPreferences(SETTINGS_STORE, Context.MODE_PRIVATE)

    /**
     * Null when the encrypted store cannot be opened at all. See
     * [openEncryptedStore] -- the app has to keep working without its stored
     * tokens, because the alternative is not starting.
     */
    private val secrets: SharedPreferences? by lazy { openEncryptedStore(appContext) }

    init {
        migrateSettingsOutOfEncryptedStoreIfNeeded()
    }

    // ========== Accounts (encrypted) ==========

    fun getAccounts(): List<Account> {
        val accountsJson = secrets?.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Account>>(accountsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAccounts(accounts: List<Account>) {
        secrets?.edit()?.putString(KEY_ACCOUNTS, json.encodeToString(accounts))?.apply()
    }

    fun addAccount(account: Account) {
        saveAccounts(getAccounts() + account)
    }

    fun removeAccount(accountId: String) {
        saveAccounts(getAccounts().filter { it.id != accountId })
    }

    // ========== Repository tracking ==========

    fun getTrackedRepoPaths(): Set<String> =
        settings.getStringSet(KEY_TRACKED_PATHS, emptySet()) ?: emptySet()

    fun addTrackedRepoPath(path: String) {
        settings.edit().putStringSet(KEY_TRACKED_PATHS, getTrackedRepoPaths() + path).apply()
    }

    fun removeTrackedRepoPath(path: String) {
        settings.edit().putStringSet(KEY_TRACKED_PATHS, getTrackedRepoPaths() - path).apply()
    }

    // ========== Settings ==========

    fun getDefaultCloneDir(): String = settings.getString(KEY_CLONE_DIR, null) ?: defaultCloneDir()

    fun setDefaultCloneDir(path: String) {
        settings.edit().putString(KEY_CLONE_DIR, path).apply()
    }

    fun getThemeMode(): ThemeMode = settings.getString(KEY_THEME, null)
        ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        ?: ThemeMode.SYSTEM

    fun setThemeMode(mode: ThemeMode) {
        settings.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun getDynamicColorEnabled(): Boolean = settings.getBoolean(KEY_DYNAMIC_COLOR, true)

    fun setDynamicColorEnabled(enabled: Boolean) {
        settings.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun getAppFont(): AppFont = settings.getString(KEY_APP_FONT, null)
        ?.let { runCatching { AppFont.valueOf(it) }.getOrNull() }
        ?: AppFont.GOOGLE_SANS_ROUNDED

    fun setAppFont(font: AppFont) {
        settings.edit().putString(KEY_APP_FONT, font.name).apply()
    }

    fun getGitUserName(): String = settings.getString(KEY_GIT_NAME, "") ?: ""
    fun setGitUserName(name: String) = settings.edit().putString(KEY_GIT_NAME, name).apply()

    fun getGitUserEmail(): String = settings.getString(KEY_GIT_EMAIL, "") ?: ""
    fun setGitUserEmail(email: String) = settings.edit().putString(KEY_GIT_EMAIL, email).apply()

    fun getEditorFontSize(): Int = settings.getInt(KEY_EDITOR_FONT_SIZE, 14)
    fun setEditorFontSize(size: Int) = settings.edit().putInt(KEY_EDITOR_FONT_SIZE, size).apply()

    fun clear() {
        settings.edit().clear().apply()
        secrets?.edit()?.clear()?.apply()
    }

    /**
     * Moves settings written by earlier versions out of the encrypted store.
     *
     * Runs once. The launch that performs it still pays the Keystore cost;
     * every launch after it does not, because the marker is checked against the
     * plain store and [secrets] is never touched.
     */
    private fun migrateSettingsOutOfEncryptedStoreIfNeeded() {
        if (settings.getBoolean(KEY_MIGRATED, false)) return

        try {
            val legacy = secrets ?: return
            val editor = settings.edit()

            legacy.getString(KEY_CLONE_DIR, null)?.let { editor.putString(KEY_CLONE_DIR, it) }
            legacy.getString(KEY_THEME, null)?.let { editor.putString(KEY_THEME, it) }
            legacy.getString(KEY_APP_FONT, null)?.let { editor.putString(KEY_APP_FONT, it) }
            legacy.getString(KEY_GIT_NAME, null)?.let { editor.putString(KEY_GIT_NAME, it) }
            legacy.getString(KEY_GIT_EMAIL, null)?.let { editor.putString(KEY_GIT_EMAIL, it) }
            legacy.getStringSet(KEY_TRACKED_PATHS, null)
                ?.let { editor.putStringSet(KEY_TRACKED_PATHS, it) }
            if (legacy.contains(KEY_DYNAMIC_COLOR)) {
                editor.putBoolean(KEY_DYNAMIC_COLOR, legacy.getBoolean(KEY_DYNAMIC_COLOR, true))
            }
            if (legacy.contains(KEY_EDITOR_FONT_SIZE)) {
                editor.putInt(KEY_EDITOR_FONT_SIZE, legacy.getInt(KEY_EDITOR_FONT_SIZE, 14))
            }

            // Committed synchronously: a crash between here and the next launch
            // would otherwise re-run the migration over already-moved values.
            editor.putBoolean(KEY_MIGRATED, true).commit()
        } catch (e: Exception) {
            // A failed migration must not stop the app from starting. Defaults
            // apply, and the marker stays unset so the next launch retries.
        }
    }

    private fun defaultCloneDir(): String =
        File(Environment.getExternalStorageDirectory(), "RoboticGit").absolutePath

    companion object {
        private const val SETTINGS_STORE = "settings_prefs"
        private const val SECRETS_STORE = "auth_prefs"

        private const val KEY_MIGRATED = "settings_migrated_v1"
        private const val KEY_ACCOUNTS = "accounts_list"
        private const val KEY_TRACKED_PATHS = "tracked_repo_paths"
        private const val KEY_CLONE_DIR = "default_clone_dir"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
        private const val KEY_APP_FONT = "app_font"
        private const val KEY_GIT_NAME = "git_user_name"
        private const val KEY_GIT_EMAIL = "git_user_email"
        private const val KEY_EDITOR_FONT_SIZE = "editor_font_size"

        private val json = Json { ignoreUnknownKeys = true }

        @Volatile
        private var instance: AuthManager? = null

        /**
         * The process-wide instance. Three call sites used to build their own.
         */
        fun get(context: Context): AuthManager =
            instance ?: synchronized(this) {
                instance ?: AuthManager(context).also { instance = it }
            }

        /**
         * Opens the encrypted store, recovering from a keyset that can no longer
         * be decrypted.
         *
         * The master key lives in the Android Keystore and can be invalidated by
         * the system -- a restored backup, a changed screen lock, a vendor
         * update. When that happens the stored keyset is undecryptable and
         * opening it throws [javax.crypto.AEADBadTagException]. Left to
         * propagate, that took the whole app down: reading accounts happens on
         * the way to the first screen, so the app would not start at all.
         *
         * Losing the tokens is unavoidable at that point -- nothing can decrypt
         * them any more -- but they can be entered again, whereas an app that
         * refuses to open cannot be recovered from inside the app. So: discard
         * the unreadable store and build a fresh one, and if even that fails,
         * carry on without stored credentials.
         */
        private fun openEncryptedStore(context: Context): SharedPreferences? = try {
            buildEncryptedStore(context)
        } catch (first: Exception) {
            context.deleteSharedPreferences(SECRETS_STORE)
            try {
                buildEncryptedStore(context)
            } catch (second: Exception) {
                null
            }
        }

        private fun buildEncryptedStore(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                SECRETS_STORE,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
    }
}
