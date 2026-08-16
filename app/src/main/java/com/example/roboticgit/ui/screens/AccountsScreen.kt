package com.example.roboticgit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.example.roboticgit.ui.components.AppAlertDialog
import com.example.roboticgit.ui.theme.ShapeTokens
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.roboticgit.data.model.AccountType
import com.example.roboticgit.ui.viewmodel.SettingsViewModel
import com.example.roboticgit.ui.viewmodel.ValidationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val accounts by viewModel.accounts.collectAsState()
    val validationStatus by viewModel.validationStatus.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedService by remember { mutableStateOf<AccountType?>(null) }

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
            val service = selectedService
            if (service == null) {
                ServiceSelectionDialog(
                    onServiceSelected = { selectedService = it },
                    onDismiss = { showAddDialog = false }
                )
            } else {
                val onDismiss = {
                    showAddDialog = false
                    selectedService = null
                    viewModel.resetValidationStatus()
                }
                when (service) {
                    AccountType.GITHUB -> {
                        AddGitHubAccountDialog(
                            onDismiss = onDismiss,
                            onManualAdd = { token ->
                                viewModel.addGitHubAccountManual(token)
                            },
                            validationStatus = validationStatus
                        )
                    }
                    AccountType.GITLAB, AccountType.GITEA, AccountType.CUSTOM -> {
                        AddGenericAccountDialog(
                            type = service,
                            onDismiss = onDismiss,
                            onAdd = { name, url, token ->
                                viewModel.addAccountGeneric(name, url, token, service)
                            },
                            validationStatus = validationStatus
                        )
                    }
                }
            }
        }

        LaunchedEffect(validationStatus) {
            if (validationStatus is ValidationStatus.Success) {
                showAddDialog = false
                selectedService = null
            }
        }
    }
}

@Composable
fun ServiceSelectionDialog(
    onServiceSelected: (AccountType) -> Unit,
    onDismiss: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = "Choose Service",
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = ShapeTokens.Card,
            tonalElevation = 2.dp
        ) {
            Column {
                ServiceItem("GitHub", Icons.Default.Public) { onServiceSelected(AccountType.GITHUB) }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                ServiceItem("GitLab", Icons.Default.Code) { onServiceSelected(AccountType.GITLAB) }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                ServiceItem("Gitea / Forgejo", Icons.Default.Cloud) { onServiceSelected(AccountType.GITEA) }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                ServiceItem("Other (Custom URL)", Icons.Default.Dns) { onServiceSelected(AccountType.CUSTOM) }
            }
        }
    }
}

@Composable
fun ServiceItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun AddGitHubAccountDialog(
    onDismiss: () -> Unit,
    onManualAdd: (String) -> Unit,
    validationStatus: ValidationStatus
) {
    var token by remember { mutableStateOf("") }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = "Add GitHub Account",
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        // Personal access tokens only. The browser flow needed a client secret,
        // and a secret shipped inside an APK is not a secret.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Paste a personal access token. A fine-grained token needs " +
                    "Contents: Read and write to push, and Metadata: Read.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("GitHub Token") },
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.TextField
            )
            Button(
                onClick = { onManualAdd(token) },
                enabled = token.isNotBlank() && validationStatus !is ValidationStatus.Loading,
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.TextField
            ) {
                Text("Verify & Add")
            }
        }
        if (validationStatus is ValidationStatus.Loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
        if (validationStatus is ValidationStatus.Error) {
            Text(
                validationStatus.message, 
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun AddGenericAccountDialog(
    type: AccountType,
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit,
    validationStatus: ValidationStatus
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(if (type == AccountType.GITLAB) "https://gitlab.com" else "") }
    var token by remember { mutableStateOf("") }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = "Add ${type.name.lowercase().replaceFirstChar { it.uppercase() }} Account",
        confirmButton = {
            Button(
                onClick = { onAdd(name, url, token) }, 
                enabled = name.isNotBlank() && url.isNotBlank() && token.isNotBlank() && validationStatus !is ValidationStatus.Loading
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Account Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.TextField,
                placeholder = { Text("e.g. My Gitea") }
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Instance URL") },
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.TextField,
                placeholder = { Text(if (type == AccountType.GITEA) "https://gitea.com" else "Git Server URL") }
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Access Token") },
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.TextField
            )
            if (validationStatus is ValidationStatus.Loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            if (validationStatus is ValidationStatus.Error) {
                Text(
                    validationStatus.message, 
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
