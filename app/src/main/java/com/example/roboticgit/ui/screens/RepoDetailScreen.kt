package com.example.roboticgit.ui.screens

import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roboticgit.ui.viewmodel.RepoDetailUiState
import com.example.roboticgit.ui.viewmodel.RepoDetailViewModel
import com.example.roboticgit.ui.viewmodel.RepoDetailViewModelFactory
import org.eclipse.jgit.revwalk.RevCommit
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    repoName: String,
    onBack: () -> Unit
) {
    val rootDir = remember {
        File(Environment.getExternalStorageDirectory(), "RoboticGit")
    }

    val viewModel: RepoDetailViewModel = viewModel(
        factory = RepoDetailViewModelFactory(rootDir, repoName)
    )
    val uiState by viewModel.uiState.collectAsState()
    
    var commitMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(repoName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    TextButton(onClick = { viewModel.pull() }) { Text("Pull") }
                    TextButton(onClick = { viewModel.push() }) { Text("Push") }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val state = uiState) {
                is RepoDetailUiState.Loading -> {
                     CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is RepoDetailUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        // Status Section
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Status", style = MaterialTheme.typography.titleMedium)
                                Text(state.status.ifEmpty { "Clean" })
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = commitMessage,
                                    onValueChange = { commitMessage = it },
                                    label = { Text("Commit Message") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = { 
                                        viewModel.commit(commitMessage)
                                        commitMessage = ""
                                    },
                                    enabled = commitMessage.isNotBlank(),
                                    modifier = Alignment.End.let { Modifier.align(it) }
                                ) {
                                    Text("Commit")
                                }
                            }
                        }

                        Text("Commits", style = MaterialTheme.typography.titleLarge)
                        LazyColumn {
                            items(state.commits) { commit ->
                                CommitItem(commit)
                                HorizontalDivider()
                            }
                        }
                    }
                }
                is RepoDetailUiState.Error -> {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun CommitItem(commit: RevCommit) {
    ListItem(
        headlineContent = { Text(commit.shortMessage) },
        supportingContent = { 
            Text("${commit.authorIdent.name} - ${Date(commit.commitTime * 1000L)}") 
        }
    )
}
