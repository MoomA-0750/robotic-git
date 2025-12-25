package com.example.roboticgit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
                    supportingContent = { Text("${account.type} - ${account.baseUrl ?: "Default"}") },
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
                onManualAdd = { name, token ->
                    viewModel.addGitHubAccountManual(name, token)
                    showAddDialog = false
                },
                validationStatus = validationStatus
            )
        }
    }
}

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onGitHubLogin: () -> Unit,
    onManualAdd: (String, String) -> Unit,
    validationStatus: ValidationStatus
) {
    var name by remember { mutableStateOf("") }
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
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Account Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("GitHub Token") },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                Button(onClick = { onManualAdd(name, token) }) {
                    Text("Add")
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
