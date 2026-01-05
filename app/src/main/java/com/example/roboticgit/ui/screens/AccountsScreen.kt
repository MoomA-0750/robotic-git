package com.example.roboticgit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.roboticgit.ui.viewmodel.SettingsViewModel
import com.example.roboticgit.ui.viewmodel.ValidationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsState()
    val validationStatus by viewModel.validationStatus.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Account")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            items(accounts) { account ->
                ListItem(
                    headlineContent = { Text(account.name) },
                    supportingContent = { Text("${account.type} ${if (account.baseUrl != null) "- ${account.baseUrl}" else ""}") },
                    leadingContent = {
                        if (account.avatarUrl != null) {
                            AsyncImage(
                                model = account.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Default Avatar",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeAccount(account.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                )
                HorizontalDivider()
            }
        }

        if (showAddDialog) {
            AddAccountDialog(
                onDismiss = { showAddDialog = false },
                onGitHubLogin = { viewModel.startGitHubLogin(context) },
                onManualAdd = { token ->
                    viewModel.addGitHubAccountManual(token)
                },
                validationStatus = validationStatus
            )
        }
        
        // Close dialog on success
        LaunchedEffect(validationStatus) {
            if (validationStatus is ValidationStatus.Success) {
                showAddDialog = false
            }
        }
    }
}

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onGitHubLogin: () -> Unit,
    onManualAdd: (String) -> Unit,
    validationStatus: ValidationStatus
) {
    var token by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("select") } // "select", "manual"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add GitHub Account") },
        text = {
            Column {
                if (mode == "select") {
                    Button(
                        onClick = onGitHubLogin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Login with Browser")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { mode = "manual" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use Personal Access Token instead")
                    }
                } else {
                    Text("Enter your Personal Access Token. The account name and avatar will be fetched automatically.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("GitHub Token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                
                if (validationStatus is ValidationStatus.Loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                
                if (validationStatus is ValidationStatus.Error) {
                    Text(
                        validationStatus.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (mode == "manual") {
                Button(
                    onClick = { onManualAdd(token) },
                    enabled = token.isNotBlank() && validationStatus !is ValidationStatus.Loading
                ) {
                    Text("Verify & Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
