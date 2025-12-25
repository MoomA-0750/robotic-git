package com.example.roboticgit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.data.model.Account
import com.example.roboticgit.data.model.GitRepo
import com.example.roboticgit.data.model.RemoteRepo
import com.example.roboticgit.ui.viewmodel.HomeUiState
import com.example.roboticgit.ui.viewmodel.HomeViewModel
import com.example.roboticgit.ui.viewmodel.HomeViewModelFactory

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onRepoClick: (String) -> Unit
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(authManager)
    )

    LaunchedEffect(Unit) {
        viewModel.refreshAccounts()
        viewModel.loadRepositories()
    }

    val repos by viewModel.repos.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val remoteRepos by viewModel.remoteRepos.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    
    var showCloneDialog by remember { mutableStateOf(false) }
    var showRemoteDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { 
                        viewModel.fetchRemoteRepositories()
                        showRemoteDialog = true 
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Import from Remote")
                }
                Spacer(modifier = Modifier.height(16.dp))
                FloatingActionButton(onClick = { showCloneDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Clone Repository")
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = modifier.padding(paddingValues).fillMaxSize()) {
            AccountSelector(
                accounts = accounts,
                selectedAccount = selectedAccount,
                onAccountSelected = { viewModel.selectAccount(it) }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is HomeUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is HomeUiState.Success -> {
                        if (repos.isEmpty()) {
                            Text(
                                text = "No repositories found. Add one!",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            RepoList(repos = repos, onRepoClick = onRepoClick)
                        }
                    }
                    is HomeUiState.Error -> {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }

    if (showCloneDialog) {
        CloneRepositoryDialog(
            onDismiss = { showCloneDialog = false },
            onClone = { url, name ->
                viewModel.cloneRepository(url, name)
                showCloneDialog = false
            }
        )
    }

    if (showRemoteDialog) {
        RemoteRepositoriesDialog(
            repos = remoteRepos,
            selectedAccount = selectedAccount,
            onDismiss = { showRemoteDialog = false },
            onClone = { repo ->
                viewModel.cloneRepository(repo.cloneUrl, repo.name)
                showRemoteDialog = false
            }
        )
    }
}

@Composable
fun AccountSelector(
    accounts: List<Account>,
    selectedAccount: Account?,
    onAccountSelected: (Account) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedAccount?.name ?: "No Account Selected",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = selectedAccount?.type?.name ?: "Go to Settings to add one",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        onAccountSelected(account)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun RepoList(repos: List<GitRepo>, onRepoClick: (String) -> Unit) {
    LazyColumn {
        items(repos) { repo ->
            RepoItem(repo = repo, onRepoClick = onRepoClick)
            HorizontalDivider()
        }
    }
}

@Composable
fun RepoItem(repo: GitRepo, onRepoClick: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(repo.name) },
        supportingContent = {
            if (repo.isCloning) {
                Column {
                    Text("Cloning: ${repo.statusMessage}")
                    LinearProgressIndicator(
                        progress = repo.progress,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            } else {
                Text(repo.path)
            }
        },
        modifier = Modifier.clickable(enabled = !repo.isCloning) { 
            onRepoClick(repo.name) 
        }
    )
}

@Composable
fun CloneRepositoryDialog(
    onDismiss: () -> Unit,
    onClone: (String, String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone Repository") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Repository URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Local Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onClone(url, name) },
                enabled = url.isNotBlank() && name.isNotBlank()
            ) {
                Text("Clone")
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
fun RemoteRepositoriesDialog(
    repos: List<RemoteRepo>,
    selectedAccount: Account?,
    onDismiss: () -> Unit,
    onClone: (RemoteRepo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import from ${selectedAccount?.name ?: "Remote"}") },
        text = {
            if (repos.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No repositories found.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(repos) { repo ->
                        ListItem(
                            headlineContent = { Text(repo.fullName) },
                            supportingContent = { Text(if (repo.private) "Private" else "Public") },
                            modifier = Modifier.clickable { onClone(repo) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
