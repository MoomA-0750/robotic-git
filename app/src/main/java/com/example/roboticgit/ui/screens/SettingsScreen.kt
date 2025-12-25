package com.example.roboticgit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.roboticgit.data.model.ThemeMode
import com.example.roboticgit.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAccounts: () -> Unit,
    viewModel: SettingsViewModel
) {
    val defaultCloneDir by viewModel.defaultCloneDir.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState()
    val gitUserName by viewModel.gitUserName.collectAsState()
    val gitUserEmail by viewModel.gitUserEmail.collectAsState()
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showIdentityDialog by remember { mutableStateOf(false) }

    // Directory Picker Launcher
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val path = it.path ?: ""
            val cleanPath = if (path.contains(":")) {
                val split = path.split(":")
                if (split.size > 1) "/storage/emulated/0/${split[1]}" else path
            } else {
                path
            }
            viewModel.onDefaultCloneDirChange(cleanPath)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item {
                SectionHeader("Identity")
                ListItem(
                    headlineContent = { Text(gitUserName.ifBlank { "Git Username" }) },
                    supportingContent = { Text(gitUserEmail.ifBlank { "Set email for Gravatar" }) },
                    leadingContent = {
                        if (gitUserEmail.isNotBlank()) {
                            AsyncImage(
                                model = viewModel.getGravatarUrl(gitUserEmail),
                                contentDescription = "Gravatar",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp))
                        }
                    },
                    trailingContent = { Icon(Icons.Default.Edit, contentDescription = "Edit") },
                    modifier = Modifier.clickable { showIdentityDialog = true }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            item {
                SectionHeader("Appearance")
                
                // Theme Selection
                val themeIcon = when (themeMode) {
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                    ThemeMode.DARK -> Icons.Default.DarkMode
                    ThemeMode.SYSTEM -> Icons.Default.BrightnessMedium
                }
                ListItem(
                    headlineContent = { Text("App Theme") },
                    supportingContent = { Text(themeMode.name) },
                    leadingContent = { Icon(themeIcon, contentDescription = null) },
                    modifier = Modifier.clickable { showThemeDialog = true }
                )

                // Dynamic Color Toggle
                ListItem(
                    headlineContent = { Text("Dynamic Color") },
                    supportingContent = { Text("Use system wallpaper colors (Android 12+)") },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = dynamicColorEnabled,
                            onCheckedChange = { viewModel.onDynamicColorChange(it) }
                        )
                    }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            item {
                SectionHeader("General")
                ListItem(
                    headlineContent = { Text("Accounts") },
                    supportingContent = { Text("Manage connected GitHub/Gitea accounts") },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable { onNavigateToAccounts() }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            item {
                SectionHeader("Storage")
                ListItem(
                    headlineContent = { Text("Default Clone Directory") },
                    supportingContent = { Text(defaultCloneDir) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { directoryPickerLauncher.launch(null) }) {
                            Icon(Icons.Default.Folder, contentDescription = "Select Folder")
                        }
                    },
                    modifier = Modifier.clickable { directoryPickerLauncher.launch(null) }
                )
            }
        }

        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentMode = themeMode,
                onModeSelected = { 
                    viewModel.onThemeModeChange(it)
                    showThemeDialog = false
                },
                onDismiss = { showThemeDialog = false }
            )
        }

        if (showIdentityDialog) {
            IdentityEditDialog(
                initialName = gitUserName,
                initialEmail = gitUserEmail,
                onSave = { name, email ->
                    viewModel.onGitUserNameChange(name)
                    viewModel.onGitUserEmailChange(email)
                    showIdentityDialog = false
                },
                onDismiss = { showIdentityDialog = false }
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun IdentityEditDialog(
    initialName: String,
    initialEmail: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Identity") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Git User Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Git User Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, email) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ThemeSelectionDialog(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(mode.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
