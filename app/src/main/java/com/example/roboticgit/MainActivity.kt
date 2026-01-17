package com.example.roboticgit

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.data.model.ThemeMode
import com.example.roboticgit.ui.RoboticGitApp
import com.example.roboticgit.ui.theme.RoboticGitTheme
import com.example.roboticgit.ui.viewmodel.SettingsViewModel
import com.example.roboticgit.ui.viewmodel.SettingsViewModelFactory

class MainActivity : ComponentActivity() {
    
    private lateinit var settingsViewModel: SettingsViewModel

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        val authManager = AuthManager(this)
        settingsViewModel = ViewModelProvider(this, SettingsViewModelFactory(authManager))[SettingsViewModel::class.java]

        checkStoragePermission()
        handleIntent(intent)

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val dynamicColor by settingsViewModel.dynamicColorEnabled.collectAsState()
            val appFont by settingsViewModel.appFont.collectAsState()
            
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            RoboticGitTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                appFont = appFont
            ) {
                val windowSizeClass = calculateWindowSizeClass(this)
                RoboticGitApp(
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null && data.toString().startsWith("roboticgit://oauth")) {
            val code = data.getQueryParameter("code")
            if (code != null) {
                settingsViewModel.handleOAuthCode(code)
            }
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please allow 'All files access' for Git operations", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${packageName}")
                startActivity(intent)
            }
        }
    }
}
