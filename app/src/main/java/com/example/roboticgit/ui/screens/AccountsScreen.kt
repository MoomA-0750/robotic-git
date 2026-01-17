package com.example.roboticgit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val context = LocalContext.current
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
                when (service) {
                    AccountType.GITHUB -> {
                        AddGitHubAccountDialog(
                            onDismiss = { 
                                showAddDialog = false
                                selectedService = null
                            },
                            onGitHubLogin = { viewModel.startGitHubLogin(context) },
                            onManualAdd = { token ->
                                viewModel.addGitHubAccountManual(token)
                            },
                            validationStatus = validationStatus
                        )
                    }
                    AccountType.GITLAB -> {
                        AddGitLabAccountDialog(
                            onDismiss = { 
                                showAddDialog = false
                                selectedService = null
                            },
                            onAdd = { url, token ->
                                // TODO: Implement GitLab add
                            },
                            validationStatus = validationStatus
                        )
                    }
                    AccountType.GITEA -> {
                        AddGiteaAccountDialog(
                            onDismiss = { 
                                showAddDialog = false
                                selectedService = null
                            },
                            onAdd = { url, token ->
                                // TODO: Implement Gitea add
                            },
                            validationStatus = validationStatus
                        )
                    }
                    AccountType.CUSTOM -> {
                        AddCustomAccountDialog(
                            onDismiss = { 
                                showAddDialog = false
                                selectedService = null
                            },
                            onAdd = { url, token ->
                                // // TODO: Implement Custom add (Generic Git)
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
fun ModernAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true),
        modifier = Modifier.clip(RoundedCornerShape(28.dp)),
        title = { 
            Text(
                text = title, 
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal
            ) 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                content = content
            )
        },
        confirmButton = confirmButton ?: {},
        dismissButton = dismissButton,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
fun ServiceSelectionDialog(
    onServiceSelected: (AccountType) -> Unit,
    onDismiss: () -> Unit
) {
    ModernAlertDialog(
        onDismissRequest = onDismiss,
        title = "Choose Service",
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
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
    onGitHubLogin: () -> Unit,
    onManualAdd: (String) -> Unit,
    validationStatus: ValidationStatus
) {
    var token by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("select") }

    ModernAlertDialog(
        onDismissRequest = onDismiss,
        title = "Add GitHub Account",
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        if (mode == "select") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGitHubLogin, 
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Login with Browser")
                }
                TextButton(
                    onClick = { mode = "manual" }, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use Personal Access Token instead")
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Generate a PAT with 'repo' and 'user' scopes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub Token") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = { onManualAdd(token) }, 
                    enabled = token.isNotBlank() && validationStatus !is ValidationStatus.Loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Verify & Add")
                }
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
fun AddGitLabAccountDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    validationStatus: ValidationStatus
) {
    var url by remember { mutableStateOf("https://gitlab.com") }
    var token by remember { mutableStateOf("") }

    ModernAlertDialog(
        onDismissRequest = onDismiss,
        title = "Add GitLab Account",
        confirmButton = {
            Button(
                onClick = { onAdd(url, token) }, 
                enabled = url.isNotBlank() && token.isNotBlank() && validationStatus !is ValidationStatus.Loading
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("GitLab URL") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Personal Access Token") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        if (validationStatus is ValidationStatus.Loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
        if (validationStatus is ValidationStatus.Error) Text(validationStatus.message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun AddGiteaAccountDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    validationStatus: ValidationStatus
) {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    ModernAlertDialog(
        onDismissRequest = onDismiss,
        title = "Add Gitea Account",
        confirmButton = {
            Button(
                onClick = { onAdd(url, token) }, 
                enabled = url.isNotBlank() && token.isNotBlank() && validationStatus !is ValidationStatus.Loading
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Instance URL (e.g. https://gitea.com)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Access Token") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        if (validationStatus is ValidationStatus.Loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
        if (validationStatus is ValidationStatus.Error) Text(validationStatus.message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun AddCustomAccountDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    validationStatus: ValidationStatus
) {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    ModernAlertDialog(
        onDismissRequest = onDismiss,
        title = "Add Custom Git Account",
        confirmButton = {
            Button(
                onClick = { onAdd(url, token) }, 
                enabled = url.isNotBlank() && token.isNotBlank() && validationStatus !is ValidationStatus.Loading
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Git Server URL") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Access Token") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        if (validationStatus is ValidationStatus.Loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
        if (validationStatus is ValidationStatus.Error) Text(validationStatus.message, color = MaterialTheme.colorScheme.error)
    }
}
