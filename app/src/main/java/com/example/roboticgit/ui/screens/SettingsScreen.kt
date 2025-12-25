package com.example.roboticgit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.ui.viewmodel.SettingsViewModel
import com.example.roboticgit.ui.viewmodel.SettingsViewModelFactory
import com.example.roboticgit.ui.viewmodel.ValidationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val authManager = remember { AuthManager(context) }
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(authManager)
    )

    val githubToken by viewModel.githubToken.collectAsState()
    val giteaBaseUrl by viewModel.giteaBaseUrl.collectAsState()
    val giteaToken by viewModel.giteaToken.collectAsState()
    val validationStatus by viewModel.validationStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("GitHub Configuration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = githubToken,
                onValueChange = { viewModel.onGitHubTokenChange(it) },
                label = { Text("Personal Access Token") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { 
                    focusManager.clearFocus()
                    viewModel.saveAndVerifyGitHub()
                }),
                isError = validationStatus is ValidationStatus.Error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { 
                        focusManager.clearFocus()
                        viewModel.saveAndVerifyGitHub() 
                    },
                    enabled = validationStatus !is ValidationStatus.Loading
                ) {
                    if (validationStatus is ValidationStatus.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying...")
                    } else {
                        Text("Save & Verify")
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))

                // Feedback UI
                when (val status = validationStatus) {
                    is ValidationStatus.Success -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Valid", tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Token saved & verified", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                    }
                    is ValidationStatus.Error -> {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Gitea Configuration (Optional)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = giteaBaseUrl,
                onValueChange = { viewModel.onGiteaConfigChange(it, giteaToken) },
                label = { Text("Base URL") },
                placeholder = { Text("https://gitea.com/api/v1/") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = giteaToken,
                onValueChange = { viewModel.onGiteaConfigChange(giteaBaseUrl, it) },
                label = { Text("Access Token") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
        }
    }
}
