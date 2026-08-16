package com.example.roboticgit.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.roboticgit.R
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.data.CommitChange
import com.example.roboticgit.data.RepoFile
import com.example.roboticgit.data.model.BranchInfo
import com.example.roboticgit.data.model.ConflictFile
import com.example.roboticgit.data.model.FileState
import com.example.roboticgit.data.model.FileStatus
import com.example.roboticgit.data.model.MergeResult
import com.example.roboticgit.data.model.MergeStatus
import com.example.roboticgit.data.model.RemoteInfo
import com.example.roboticgit.ui.theme.JetBrainsMono
import com.example.roboticgit.ui.theme.extendedColors
import com.example.roboticgit.ui.viewmodel.RepoDetailUiState
import com.example.roboticgit.ui.viewmodel.RepoDetailViewModel
import com.example.roboticgit.ui.viewmodel.RepoDetailViewModelFactory
import kotlinx.coroutines.launch
import org.eclipse.jgit.revwalk.RevCommit
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    repoName: String,
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val rootDir = remember { File(authManager.getDefaultCloneDir()) }
    // EncryptedSharedPreferences read; must not happen on every recomposition.
    val editorFontSize = remember { authManager.getEditorFontSize().toFloat() }

    // key = repoName so switching repos in the two-pane layout gets a fresh
    // ViewModel instead of reusing the one bound to the first repo opened.
    val viewModel: RepoDetailViewModel = viewModel(
        key = repoName,
        factory = RepoDetailViewModelFactory(authManager, rootDir, repoName)
    )
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var commitMessage by remember { mutableStateOf("") }
    val pagerState = rememberPagerState(pageCount = { 4 })
    
    var diffFile by rememberSaveable { mutableStateOf<String?>(null) }
    var diffText by rememberSaveable { mutableStateOf<String?>(null) }

    var editingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var editingText by rememberSaveable { mutableStateOf("") }

    var showCreateBranchDialog by remember { mutableStateOf(false) }
    var branchToDelete by remember { mutableStateOf<BranchInfo?>(null) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showRemotesDialog by remember { mutableStateOf(false) }
    var conflictFileToResolve by remember { mutableStateOf<String?>(null) }
    var conflictContent by remember { mutableStateOf<ConflictFile?>(null) }

    val isMerging by viewModel.isMerging.collectAsState()
    val mergeResult by viewModel.mergeResult.collectAsState()
    val remotes by viewModel.remotes.collectAsState()
    val conflictingFiles by viewModel.conflictingFiles.collectAsState()

    val scope = rememberCoroutineScope()

    // Track staged files for contextual top bar
    val stagedCount = (uiState as? RepoDetailUiState.Success)?.fileStatuses?.count { it.isStaged } ?: 0
    val totalChanges = (uiState as? RepoDetailUiState.Success)?.fileStatuses?.size ?: 0
    val showStagedTopBar = pagerState.currentPage == 0 && stagedCount > 0

    Scaffold(
        topBar = {
            if (showStagedTopBar) {
                // Contextual top bar for staged files
                StagedFilesTopBar(
                    stagedCount = stagedCount,
                    totalCount = totalChanges,
                    onClearStaged = {
                        (uiState as? RepoDetailUiState.Success)?.fileStatuses
                            ?.filter { it.isStaged }
                            ?.forEach { viewModel.toggleStage(it) }
                    },
                    onStageAll = {
                        (uiState as? RepoDetailUiState.Success)?.fileStatuses
                            ?.filter { !it.isStaged }
                            ?.forEach { viewModel.toggleStage(it) }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(repoName)
                            if (uiState is RepoDetailUiState.Success) {
                                val state = uiState as RepoDetailUiState.Success
                                Text(
                                    text = state.currentBranch ?: "No branch",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
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
        },
        floatingActionButton = {
            if (pagerState.currentPage == 3) {
                FloatingActionButton(onClick = { showCreateBranchDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Branch")
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // Merge in progress banner
            if (isMerging) {
                MergeBanner(
                    conflictCount = conflictingFiles.size,
                    onAbort = { viewModel.abortMerge() },
                    onComplete = { viewModel.completeMerge() }
                )
            }

            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Changes") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Files") }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text("History") }
                )
                Tab(
                    selected = pagerState.currentPage == 3,
                    onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                    text = { Text("Branches") }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is RepoDetailUiState.Loading -> {
                         CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is RepoDetailUiState.Success -> {
                        // HorizontalPager provides natural Lateral Transition for tabs
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true,
                            beyondViewportPageCount = 1
                        ) { page ->
                            when (page) {
                                0 -> ChangesView(
                                    fileStatuses = state.fileStatuses,
                                    commitMessage = commitMessage,
                                    onMessageChange = { commitMessage = it },
                                    onCommit = {
                                        viewModel.commit(commitMessage)
                                        commitMessage = ""
                                    },
                                    onToggleStage = viewModel::toggleStage,
                                    onRollback = viewModel::rollbackFile,
                                    onFileClick = { file ->
                                        diffFile = file.path
                                        scope.launch {
                                            diffText = viewModel.getFileDiff(file)
                                        }
                                    },
                                    onEditClick = { file ->
                                        scope.launch {
                                            editingText = viewModel.readFile(file.path)
                                            editingPath = file.path
                                        }
                                    },
                                    onResolveConflict = { file ->
                                        scope.launch {
                                            conflictFileToResolve = file.path
                                            conflictContent = viewModel.getConflictContent(file.path)
                                        }
                                    }
                                )
                                1 -> FilesView(
                                    viewModel = viewModel,
                                    onFileClick = { path ->
                                        scope.launch {
                                            editingText = viewModel.readFile(path)
                                            editingPath = path
                                        }
                                    }
                                )
                                2 -> HistoryView(
                                    commits = state.commits,
                                    getGravatarUrl = viewModel::getGravatarUrl,
                                    viewModel = viewModel
                                )
                                3 -> BranchesView(
                                    branches = state.branches,
                                    onBranchClick = { branch ->
                                        if (!branch.isCurrent) {
                                            viewModel.checkoutBranch(branch.name)
                                        }
                                    },
                                    onDeleteBranch = { branch ->
                                        branchToDelete = branch
                                    },
                                    onMergeClick = { showMergeDialog = true },
                                    onRemotesClick = { showRemotesDialog = true }
                                )
                            }
                        }
                    }
                    is RepoDetailUiState.Error -> {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        errorMessage?.let { msg ->
            if (msg == "UNCOMMITTED_CHANGES") {
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    title = { Text("Uncommitted Changes") },
                    text = { Text("You have uncommitted changes. Please commit or stash them before switching branches, or force checkout (Warning: this will lose changes).") },
                    confirmButton = {
                        TextButton(onClick = { 
                            viewModel.clearError()
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            viewModel.clearError()
                        }) { Text("Force (Stub)") }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    title = { Text("Error") },
                    text = { Text(msg) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
                    }
                )
            }
        }

        if (showCreateBranchDialog) {
            CreateBranchDialog(
                onDismiss = { showCreateBranchDialog = false },
                onCreate = { name ->
                    viewModel.createBranch(name)
                    showCreateBranchDialog = false
                }
            )
        }

        branchToDelete?.let { branch ->
            DeleteBranchDialog(
                branchName = branch.name,
                onDismiss = { branchToDelete = null },
                onConfirm = { force ->
                    viewModel.deleteBranch(branch.name, force)
                    branchToDelete = null
                }
            )
        }

        if (diffFile != null) {
            DiffDialog(
                fileName = diffFile ?: "",
                diffText = diffText,
                onDismiss = {
                    diffFile = null
                    diffText = null
                }
            )
        }

        editingPath?.let { path ->
            EditorDialog(
                fileName = path.substringAfterLast("/"),
                content = editingText,
                onContentChange = { editingText = it },
                onSave = {
                    viewModel.saveFile(path, editingText)
                    editingPath = null
                },
                onDismiss = { editingPath = null },
                fontSize = editorFontSize
            )
        }

        // CommitDetailDialog removed - now handled inline in HistoryView split-screen

        // Merge dialog
        if (showMergeDialog && uiState is RepoDetailUiState.Success) {
            val state = uiState as RepoDetailUiState.Success
            MergeDialog(
                branches = state.branches.filter { !it.isCurrent && !it.isRemote },
                currentBranch = state.currentBranch ?: "",
                onMerge = { branchName, fastForward ->
                    viewModel.mergeBranch(branchName, fastForward)
                    showMergeDialog = false
                },
                onDismiss = { showMergeDialog = false }
            )
        }

        // Merge result snackbar
        mergeResult?.let { result ->
            LaunchedEffect(result) {
                // Auto-dismiss after showing
            }
            when (result.status) {
                MergeStatus.SUCCESS, MergeStatus.FAST_FORWARD -> {
                    LaunchedEffect(result) {
                        viewModel.clearMergeResult()
                    }
                }
                MergeStatus.CONFLICTING -> {
                    // Conflicts are handled by the banner
                }
                else -> {}
            }
        }

        // Remotes dialog
        if (showRemotesDialog) {
            RemotesDialog(
                remotes = remotes,
                onAddRemote = { name, url -> viewModel.addRemote(name, url) },
                onRemoveRemote = { name -> viewModel.removeRemote(name) },
                onUpdateRemote = { name, url -> viewModel.updateRemoteUrl(name, url) },
                onDismiss = { showRemotesDialog = false }
            )
        }

        // Conflict resolution dialog
        val currentConflictPath = conflictFileToResolve
        val currentConflictContent = conflictContent
        if (currentConflictPath != null && currentConflictContent != null) {
            ConflictResolveDialog(
                conflictFile = currentConflictContent,
                onResolve = { resolvedContent ->
                    viewModel.resolveConflict(currentConflictPath, resolvedContent)
                    conflictFileToResolve = null
                    conflictContent = null
                },
                onDismiss = {
                    conflictFileToResolve = null
                    conflictContent = null
                }
            )
        }
    }
}

@Composable
fun BranchesView(
    branches: List<BranchInfo>,
    onBranchClick: (BranchInfo) -> Unit,
    onDeleteBranch: (BranchInfo) -> Unit,
    onMergeClick: () -> Unit = {},
    onRemotesClick: () -> Unit = {}
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val localBranches = remember(branches) { branches.filter { !it.isRemote } }
    val remoteBranches = remember(branches) { branches.filter { it.isRemote } }
    val options = listOf("Local", "Remote")

    Column(modifier = Modifier.fillMaxSize()) {
        // Action buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onMergeClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Merge")
            }
            OutlinedButton(
                onClick = onRemotesClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Remotes")
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = { selectedIndex = index },
                    selected = selectedIndex == index
                ) {
                    Text(label)
                }
            }
        }

        val filteredBranches = if (selectedIndex == 0) localBranches else remoteBranches
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredBranches) { branch ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = branch.name,
                            fontWeight = if (branch.isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (branch.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supportingContent = {
                        branch.lastCommitMessage?.let { Text(it, maxLines = 1) }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (branch.isRemote) Icons.Default.Cloud else Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = if (branch.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (branch.isCurrent) {
                                Icon(Icons.Default.Check, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary)
                            } else if (!branch.isRemote) {
                                IconButton(onClick = { onDeleteBranch(branch) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Branch", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable { onBranchClick(branch) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun DeleteBranchDialog(
    branchName: String,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var forceDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Branch") },
        text = {
            Column {
                Text("Are you sure you want to delete branch '$branchName'?")
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = forceDelete, onCheckedChange = { forceDelete = it })
                    Text("Force delete (Warning: may lose unmerged changes)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(forceDelete) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
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
fun CreateBranchDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var branchName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Branch") },
        text = {
            OutlinedTextField(
                value = branchName,
                onValueChange = { branchName = it },
                label = { Text("Branch Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(branchName) },
                enabled = branchName.isNotBlank()
            ) {
                Text("Create")
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
fun FilesView(
    viewModel: RepoDetailViewModel,
    onFileClick: (String) -> Unit
) {
    var currentPath by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<RepoFile>>(emptyList()) }

    LaunchedEffect(currentPath) {
        files = viewModel.listFiles(currentPath)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { currentPath = "" }) { Text("root") }
            currentPath.split("/").filter { it.isNotEmpty() }.forEachIndexed { index, segment ->
                Text("/")
                TextButton(onClick = {
                    currentPath = currentPath.split("/").take(index + 1).joinToString("/")
                }) {
                    Text(segment)
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(files) { file ->
                ListItem(
                    headlineContent = { Text(file.name) },
                    leadingContent = {
                        Icon(
                            if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    },
                    modifier = Modifier.clickable {
                        if (file.isDirectory) {
                            currentPath = file.path
                        } else {
                            onFileClick(file.path)
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StagedFilesTopBar(
    stagedCount: Int,
    totalCount: Int,
    onClearStaged: () -> Unit,
    onStageAll: () -> Unit
) {
    TopAppBar(
        title = { Text("$stagedCount staged") },
        navigationIcon = {
            IconButton(onClick = onClearStaged) {
                Icon(Icons.Default.Close, contentDescription = "Clear staged")
            }
        },
        actions = {
            if (stagedCount < totalCount) {
                TextButton(onClick = onStageAll) {
                    Text("Stage All")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}

@Composable
fun ChangesView(
    fileStatuses: List<FileStatus>,
    commitMessage: String,
    onMessageChange: (String) -> Unit,
    onCommit: () -> Unit,
    onToggleStage: (FileStatus) -> Unit,
    onRollback: (FileStatus) -> Unit,
    onFileClick: (FileStatus) -> Unit,
    onEditClick: (FileStatus) -> Unit,
    onResolveConflict: (FileStatus) -> Unit = {}
) {
    val stagedCount = fileStatuses.count { it.isStaged }

    Box(modifier = Modifier.fillMaxSize()) {
        if (fileStatuses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No changes detected")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp) // Space for floating toolbar
            ) {
                items(fileStatuses) { fileStatus ->
                    FileStatusItem(
                        fileStatus = fileStatus,
                        onToggleStage = { onToggleStage(fileStatus) },
                        onRollback = { onRollback(fileStatus) },
                        onClick = { onFileClick(fileStatus) },
                        onEditClick = { onEditClick(fileStatus) },
                        onResolveConflict = { onResolveConflict(fileStatus) }
                    )
                    HorizontalDivider()
                }
            }

            // Floating Toolbar with input field and FAB
            if (fileStatuses.isNotEmpty()) {
                FloatingCommitToolbar(
                    commitMessage = commitMessage,
                    onMessageChange = onMessageChange,
                    onCommit = onCommit,
                    enabled = commitMessage.isNotBlank() && stagedCount > 0,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun FloatingCommitToolbar(
    commitMessage: String,
    onMessageChange: (String) -> Unit,
    onCommit: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Commit message input field
        TextField(
            value = commitMessage,
            onValueChange = onMessageChange,
            placeholder = { Text("Commit message") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent
            )
        )

        // Commit FAB
        FloatingActionButton(
            onClick = onCommit,
            containerColor = if (enabled)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (enabled)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Icon(Icons.Default.Check, contentDescription = "Commit")
        }
    }
}

@Composable
fun FileStatusItem(
    fileStatus: FileStatus,
    onToggleStage: () -> Unit,
    onRollback: () -> Unit,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onResolveConflict: () -> Unit = {}
) {
    val extendedColors = MaterialTheme.extendedColors
    val isConflict = fileStatus.state == FileState.CONFLICTING

    val color = when (fileStatus.state) {
        FileState.ADDED, FileState.UNTRACKED -> extendedColors.statusAdded
        FileState.MODIFIED -> extendedColors.statusModified
        FileState.REMOVED, FileState.DELETED, FileState.MISSING -> extendedColors.statusDeleted
        FileState.CONFLICTING -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fileStatus.path)
                if (isConflict) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Conflict",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        supportingContent = {
            Text(
                if (isConflict) "CONFLICT - needs resolution" else fileStatus.state.name,
                color = color,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            if (isConflict) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = "Conflict",
                    tint = MaterialTheme.colorScheme.error
                )
            } else {
                Checkbox(
                    checked = fileStatus.isStaged,
                    onCheckedChange = { onToggleStage() }
                )
            }
        },
        trailingContent = {
            Row {
                if (isConflict) {
                    FilledTonalButton(
                        onClick = onResolveConflict,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text("Resolve", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                } else {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    if (!fileStatus.isStaged && fileStatus.state != FileState.UNTRACKED) {
                        IconButton(onClick = onRollback) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Rollback", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        modifier = Modifier
            .clickable { if (!isConflict) onClick() }
            .then(
                if (isConflict) Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))
                else Modifier
            )
    )
}

@Composable
fun DiffDialog(
    fileName: String,
    diffText: String?,
    onDismiss: () -> Unit
) {
    val extendedColors = MaterialTheme.extendedColors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Diff: $fileName") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                if (diffText == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                    ) {
                        diffText.split("\n").forEach { line ->
                            val bgColor = when {
                                line.startsWith("+") -> extendedColors.diffAddedBackground
                                line.startsWith("-") -> extendedColors.diffRemovedBackground
                                else -> Color.Transparent
                            }
                            val textColor = when {
                                line.startsWith("+") -> extendedColors.diffAddedText
                                line.startsWith("-") -> extendedColors.diffRemovedText
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = line,
                                color = textColor,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgColor)
                                    .padding(horizontal = 4.dp)
                            )
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorDialog(
    fileName: String,
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    fontSize: Float = 14f,
    onFontSizeChange: ((Float) -> Unit)? = null
) {
    // Local state for dynamic font size during pinch gestures
    var currentFontSize by rememberSaveable { mutableStateOf(fontSize) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Edit: $fileName", maxLines = 1)
                            Text(
                                text = "Font: ${"%.1f".format(currentFontSize)}sp",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = onSave) {
                            Text("SAVE")
                        }
                    }
                )

                CodeEditor(
                    value = content,
                    onValueChange = onContentChange,
                    modifier = Modifier.weight(1f),
                    fontSize = currentFontSize,
                    onFontSizeChange = { newSize ->
                        currentFontSize = newSize
                        onFontSizeChange?.invoke(newSize)
                    }
                )
            }
        }
    }
}

@Composable
fun CodeEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Float = 14f,
    onFontSizeChange: ((Float) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    // Reset layout when value length changes significantly to avoid stale layout issues
    var textLayoutResult by remember(value.length) { mutableStateOf<TextLayoutResult?>(null) }

    // Track base font size at gesture start and accumulated scale
    var baseFontSize by remember { mutableStateOf(fontSize) }
    var cumulativeScale by remember { mutableStateOf(1f) }

    val fontSizeSp = fontSize.sp
    val lineHeightSp = (fontSize * 1.5f).sp

    val lineNumbers = remember(value) {
        val count = value.count { it == '\n' } + 1
        (1..count).joinToString("\n")
    }

    val gutterBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val gutterTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val whitespaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val whitespaceTransformation = remember(whitespaceColor) { WhitespaceVisualTransformation(whitespaceColor) }

    // Pinch gesture modifier - smooth continuous zoom with stabilization
    val pinchModifier = if (onFontSizeChange != null) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // Reset at gesture start
                baseFontSize = fontSize
                cumulativeScale = 1f

                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    // Only process when 2+ fingers are down
                    if (event.changes.size >= 2) {
                        val zoom = event.calculateZoom()
                        // Dead zone to filter noise (ignore very small changes)
                        if (zoom != 1f && (zoom > 1.01f || zoom < 0.99f)) {
                            cumulativeScale *= zoom
                            // Calculate new size from base, not current (prevents oscillation)
                            val newSize = (baseFontSize * cumulativeScale).coerceIn(8f, 32f)
                            if (kotlin.math.abs(newSize - fontSize) > 0.1f) {
                                onFontSizeChange(newSize)
                            }
                            // Consume to prevent scrolling during pinch
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    } else {
        Modifier
    }

    Row(modifier = modifier
        .fillMaxSize()
        .then(pinchModifier)
        .background(MaterialTheme.colorScheme.surface)) {

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(48.dp)
                .background(gutterBackground)
                .verticalScroll(scrollState)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Text(
                text = lineNumbers,
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = fontSizeSp,
                    lineHeight = lineHeightSp,
                    color = gutterTextColor
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        Box(modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .verticalScroll(scrollState)
            .horizontalScroll(rememberScrollState())
            .padding(16.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val layout = textLayoutResult ?: return@Canvas
                if (layout.lineCount == 0 || value.isEmpty()) return@Canvas

                val charWidth = fontSizeSp.toPx() * 0.6f

                try {
                    for (lineIndex in 0 until layout.lineCount) {
                        val lineStart = layout.getLineStart(lineIndex)
                        val lineEnd = layout.getLineEnd(lineIndex)
                        // Safety check: ensure indices are within bounds
                        if (lineStart < 0 || lineStart >= value.length) continue
                        val safeEnd = lineEnd.coerceIn(lineStart, value.length)
                        val lineText = value.substring(lineStart, safeEnd)

                        var leadingSpaces = 0
                        for (char in lineText) {
                            when (char) {
                                ' ' -> leadingSpaces++
                                '\t' -> leadingSpaces += 4
                                else -> break
                            }
                        }

                        val indentLevels = leadingSpaces / 4
                        if (indentLevels > 0) {
                            val top = layout.getLineTop(lineIndex)
                            val bottom = layout.getLineBottom(lineIndex)

                            for (level in 1..indentLevels) {
                                val x = (level * 4 - 2) * charWidth
                                drawLine(
                                    color = guideColor,
                                    start = Offset(x, top),
                                    end = Offset(x, bottom),
                                    strokeWidth = 1f
                                )
                            }
                        }
                    }
                } catch (_: IndexOutOfBoundsException) {
                    // Ignore layout calculation errors during recomposition
                    // This can happen when textLayoutResult is stale after configuration change
                }
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = fontSizeSp,
                    lineHeight = lineHeightSp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                onTextLayout = { textLayoutResult = it },
                visualTransformation = whitespaceTransformation
            )
        }
    }
}

class WhitespaceVisualTransformation(
    private val whitespaceColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()

        for (char in text.text) {
            when (char) {
                ' ' -> builder.append(AnnotatedString(
                    "·",
                    SpanStyle(color = whitespaceColor)
                ))
                '\t' -> builder.append(AnnotatedString(
                    "→   ",
                    SpanStyle(color = whitespaceColor)
                ))
                else -> builder.append(char.toString())
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var transformedOffset = 0
                for (i in 0 until offset.coerceAtMost(text.text.length)) {
                    if (text.text[i] == '\t') transformedOffset += 4 else transformedOffset++
                }
                return transformedOffset
            }

            override fun transformedToOriginal(offset: Int): Int {
                var currentTransformed = 0
                var originalOffset = 0
                while (currentTransformed < offset && originalOffset < text.text.length) {
                    if (text.text[originalOffset] == '\t') {
                        currentTransformed += 4
                    } else {
                        currentTransformed++
                    }
                    originalOffset++
                }
                return originalOffset.coerceAtMost(text.text.length)
            }
        }

        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }
}

@Composable
fun HistoryView(
    commits: List<RevCommit>,
    getGravatarUrl: (String) -> String,
    viewModel: RepoDetailViewModel
) {
    var selectedCommit by remember { mutableStateOf<RevCommit?>(null) }
    var commitChanges by remember { mutableStateOf<List<CommitChange>>(emptyList()) }
    var isLoadingChanges by remember { mutableStateOf(false) }
    var selectedFileDiff by remember { mutableStateOf<String?>(null) }
    var selectedFilePath by remember { mutableStateOf<String?>(null) }
    var isLoadingDiff by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Draggable split ratio: fraction of total height for top pane
    var splitFraction by remember { mutableFloatStateOf(0.4f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        Column(modifier = Modifier.fillMaxSize()) {
            // === Top: Commit list ===
            LazyColumn(
                modifier = Modifier
                    .weight(if (selectedCommit != null) splitFraction else 1f)
                    .fillMaxWidth()
            ) {
                items(commits) { commit ->
                    val isSelected = selectedCommit?.name == commit.name
                    CommitItem(
                        commit = commit,
                        getGravatarUrl = getGravatarUrl,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                selectedCommit = null
                                commitChanges = emptyList()
                                selectedFileDiff = null
                                selectedFilePath = null
                            } else {
                                selectedCommit = commit
                                selectedFileDiff = null
                                selectedFilePath = null
                                isLoadingChanges = true
                                scope.launch {
                                    commitChanges = viewModel.getCommitChanges(commit)
                                    isLoadingChanges = false
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            // === Draggable divider + Bottom detail panel ===
            selectedCommit?.let { commit ->
                // --- Draggable divider handle ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                val newFraction = splitFraction + delta / totalHeightPx.coerceAtLeast(1f)
                                splitFraction = newFraction.coerceIn(0.02f, 0.98f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Visual grip indicator
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

                // --- Bottom detail panel ---
                Column(
                    modifier = Modifier
                        .weight(1f - splitFraction)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    // Header bar with close button
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = getGravatarUrl(commit.authorIdent.emailAddress),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = commit.shortMessage,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${commit.authorIdent.name} · ${Date(commit.commitTime * 1000L)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                selectedCommit = null
                                commitChanges = emptyList()
                                selectedFileDiff = null
                                selectedFilePath = null
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close detail panel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Scrollable content area
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // Commit hash
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Hash:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = commit.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Full commit message (if different from short message)
                        if (commit.fullMessage.trim() != commit.shortMessage.trim()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = commit.fullMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        // Changed files section header
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isLoadingChanges) "Loading changes…"
                                           else "Changed Files (${commitChanges.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        if (isLoadingChanges) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        } else {
                            // Render directory tree
                            item {
                                val fileTree = remember(commitChanges) {
                                    buildFileTree(commitChanges)
                                }
                                FileTreeView(
                                    nodes = fileTree,
                                    selectedFilePath = selectedFilePath,
                                    selectedFileDiff = selectedFileDiff,
                                    isLoadingDiff = isLoadingDiff,
                                    onFileClick = { path ->
                                        if (selectedFilePath == path) {
                                            selectedFilePath = null
                                            selectedFileDiff = null
                                        } else {
                                            selectedFilePath = path
                                            selectedFileDiff = null
                                            isLoadingDiff = true
                                            scope.launch {
                                                selectedFileDiff = viewModel.getCommitFileDiff(commit, path)
                                                isLoadingDiff = false
                                            }
                                        }
                                    },
                                    depth = 0
                                )
                            }
                        }

                        // Bottom padding
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ========== File Tree Data Structures ==========

private data class FileTreeNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val changeType: String? = null,
    val children: MutableList<FileTreeNode> = mutableListOf()
)

private fun buildFileTree(changes: List<CommitChange>): List<FileTreeNode> {
    val root = mutableListOf<FileTreeNode>()
    for (change in changes) {
        val parts = change.path.split("/")
        var currentChildren = root
        for (i in parts.indices) {
            val part = parts[i]
            val isFile = i == parts.lastIndex
            val fullPath = parts.take(i + 1).joinToString("/")
            val existing = currentChildren.find { it.name == part }
            if (existing != null) {
                currentChildren = existing.children
            } else {
                val node = FileTreeNode(
                    name = part,
                    fullPath = fullPath,
                    isDirectory = !isFile,
                    changeType = if (isFile) change.changeType else null
                )
                currentChildren.add(node)
                currentChildren = node.children
            }
        }
    }
    fun sortNodes(nodes: MutableList<FileTreeNode>) {
        nodes.sortWith(compareBy<FileTreeNode> { !it.isDirectory }.thenBy { it.name })
        nodes.forEach { if (it.isDirectory) sortNodes(it.children) }
    }
    sortNodes(root)
    return root
}

// ========== File Tree Composables ==========

@Composable
private fun FileTreeView(
    nodes: List<FileTreeNode>,
    selectedFilePath: String?,
    selectedFileDiff: String?,
    isLoadingDiff: Boolean,
    onFileClick: (String) -> Unit,
    depth: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        nodes.forEach { node ->
            if (node.isDirectory) {
                DirectoryTreeNode(
                    node = node,
                    selectedFilePath = selectedFilePath,
                    selectedFileDiff = selectedFileDiff,
                    isLoadingDiff = isLoadingDiff,
                    onFileClick = onFileClick,
                    depth = depth
                )
            } else {
                FileTreeLeaf(
                    node = node,
                    isSelected = selectedFilePath == node.fullPath,
                    selectedFileDiff = selectedFileDiff,
                    isLoadingDiff = isLoadingDiff,
                    onFileClick = onFileClick,
                    depth = depth
                )
            }
        }
    }
}

@Composable
private fun DirectoryTreeNode(
    node: FileTreeNode,
    selectedFilePath: String?,
    selectedFileDiff: String?,
    isLoadingDiff: Boolean,
    onFileClick: (String) -> Unit,
    depth: Int
) {
    var isExpanded by remember { mutableStateOf(true) }
    val fileCount = remember(node) {
        fun count(n: FileTreeNode): Int =
            if (n.isDirectory) n.children.sumOf { count(it) } else 1
        count(node)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
        ) {
            Row(
                modifier = Modifier.padding(
                    start = (depth * 16 + 4).dp, end = 8.dp,
                    top = 4.dp, bottom = 4.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "$fileCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            FileTreeView(
                nodes = node.children,
                selectedFilePath = selectedFilePath,
                selectedFileDiff = selectedFileDiff,
                isLoadingDiff = isLoadingDiff,
                onFileClick = onFileClick,
                depth = depth + 1
            )
        }
    }
}

@Composable
private fun FileTreeLeaf(
    node: FileTreeNode,
    isSelected: Boolean,
    selectedFileDiff: String?,
    isLoadingDiff: Boolean,
    onFileClick: (String) -> Unit,
    depth: Int
) {
    val changeColor = when (node.changeType) {
        "ADD" -> Color(0xFF4CAF50)
        "DELETE" -> Color(0xFFF44336)
        "MODIFY" -> Color(0xFFFF9800)
        "RENAME" -> Color(0xFF2196F3)
        "COPY" -> Color(0xFF9C27B0)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val changeLabel = when (node.changeType) {
        "ADD" -> "A"
        "DELETE" -> "D"
        "MODIFY" -> "M"
        "RENAME" -> "R"
        "COPY" -> "C"
        else -> "?"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFileClick(node.fullPath) }
        ) {
            Row(
                modifier = Modifier.padding(
                    start = (depth * 16 + 8).dp, end = 8.dp,
                    top = 6.dp, bottom = 6.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = changeColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = changeLabel,
                            color = changeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = changeColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isSelected) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isSelected) "Collapse diff" else "Expand diff",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(visible = isSelected) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (depth * 16 + 8).dp, end = 8.dp, bottom = 4.dp)
            ) {
                if (isLoadingDiff) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                } else {
                    SideBySideDiffView(
                        diffText = selectedFileDiff ?: "",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
            modifier = Modifier.padding(start = (depth * 16).dp)
        )
    }
}

@Composable
fun CommitItem(
    commit: RevCommit,
    getGravatarUrl: (String) -> String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val email = commit.authorIdent.emailAddress
    ListItem(
        headlineContent = { Text(commit.shortMessage, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        supportingContent = {
            Text("${commit.authorIdent.name} · ${Date(commit.commitTime * 1000L)}")
        },
        leadingContent = {
            AsyncImage(
                model = getGravatarUrl(email),
                contentDescription = "Author Avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        },
        modifier = Modifier
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                else Modifier
            ),
        trailingContent = {
            Text(
                text = commit.name.take(7),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline
            )
        }
    )
}

// ========== Side-by-side Diff View (VS Code-style) ==========

/**
 * Represents a single line in the diff view
 */
private enum class DiffLineType {
    CONTEXT, ADDED, REMOVED, HUNK_HEADER, FILE_HEADER
}

private data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNumber: Int? = null,   // line number in old file
    val newLineNumber: Int? = null    // line number in new file
)

/**
 * A pair of lines for side-by-side view: left (old), right (new)
 */
private data class SideBySideLine(
    val left: DiffLine?,
    val right: DiffLine?,
    val isHunkHeader: Boolean = false,
    val hunkText: String = ""
)

/**
 * Parses unified diff text into a list of SideBySideLines for rendering
 */
private fun parseSideBySideDiff(diffText: String): List<SideBySideLine> {
    val lines = diffText.split("\n")
    val result = mutableListOf<SideBySideLine>()

    var oldLine = 0
    var newLine = 0
    val removedBuffer = mutableListOf<DiffLine>()
    val addedBuffer = mutableListOf<DiffLine>()

    fun flushBuffers() {
        val maxLen = maxOf(removedBuffer.size, addedBuffer.size)
        for (i in 0 until maxLen) {
            result.add(
                SideBySideLine(
                    left = removedBuffer.getOrNull(i),
                    right = addedBuffer.getOrNull(i)
                )
            )
        }
        removedBuffer.clear()
        addedBuffer.clear()
    }

    for (line in lines) {
        when {
            // File headers (diff --git, index, ---, +++)
            line.startsWith("diff ") || line.startsWith("index ") ||
            line.startsWith("--- ") || line.startsWith("+++ ") -> {
                flushBuffers()
                // Skip file-level headers, we already show the file name
            }
            // Hunk header: @@ -oldStart,oldCount +newStart,newCount @@
            line.startsWith("@@") -> {
                flushBuffers()
                // Parse line numbers from hunk header
                val regex = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@(.*)""")
                val match = regex.find(line)
                if (match != null) {
                    oldLine = match.groupValues[1].toIntOrNull() ?: 0
                    newLine = match.groupValues[2].toIntOrNull() ?: 0
                    val context = match.groupValues[3].trim()
                    result.add(
                        SideBySideLine(
                            left = null,
                            right = null,
                            isHunkHeader = true,
                            hunkText = if (context.isNotEmpty()) "@@ $context" else line
                        )
                    )
                }
            }
            // Removed line
            line.startsWith("-") -> {
                removedBuffer.add(DiffLine(
                    type = DiffLineType.REMOVED,
                    content = line.substring(1),
                    oldLineNumber = oldLine
                ))
                oldLine++
            }
            // Added line
            line.startsWith("+") -> {
                addedBuffer.add(DiffLine(
                    type = DiffLineType.ADDED,
                    content = line.substring(1),
                    newLineNumber = newLine
                ))
                newLine++
            }
            // Context line (unchanged)
            else -> {
                flushBuffers()
                val content = if (line.startsWith(" ")) line.substring(1) else line
                if (line.isNotEmpty() || (oldLine > 0 && newLine > 0)) {
                    result.add(
                        SideBySideLine(
                            left = DiffLine(
                                type = DiffLineType.CONTEXT,
                                content = content,
                                oldLineNumber = oldLine
                            ),
                            right = DiffLine(
                                type = DiffLineType.CONTEXT,
                                content = content,
                                newLineNumber = newLine
                            )
                        )
                    )
                    oldLine++
                    newLine++
                }
            }
        }
    }
    flushBuffers()
    return result
}

@Composable
fun SideBySideDiffView(
    diffText: String,
    modifier: Modifier = Modifier
) {
    val extendedColors = MaterialTheme.extendedColors
    val parsedLines = remember(diffText) { parseSideBySideDiff(diffText) }

    if (parsedLines.isEmpty()) {
        Box(
            modifier = modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No changes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }

    // Find max line number for gutter width calculation
    val maxLineNum = remember(parsedLines) {
        var maxOld = 0
        var maxNew = 0
        parsedLines.forEach { line ->
            line.left?.oldLineNumber?.let { if (it > maxOld) maxOld = it }
            line.right?.newLineNumber?.let { if (it > maxNew) maxNew = it }
        }
        maxOf(maxOld, maxNew)
    }
    val gutterWidth = remember(maxLineNum) {
        (maxLineNum.toString().length * 8 + 12).coerceAtLeast(32)
    }

    val gutterBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val gutterTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val separatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val hunkBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    val hunkTextColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(modifier = modifier) {
        val isSideBySide = maxWidth >= 600.dp

        if (isSideBySide) {
            // === Side-by-side mode ===
            // Use IntrinsicSize.Min on the parent Row so the center divider
            // can use fillMaxHeight() correctly
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                val leftScrollState = rememberScrollState()
                val rightScrollState = rememberScrollState()

                // Left pane (old file) — no verticalScroll (parent LazyColumn handles it)
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Header
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "  ← Original",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    parsedLines.forEach { sideBySideLine ->
                        if (sideBySideLine.isHunkHeader) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(hunkBg)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = sideBySideLine.hunkText,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = hunkTextColor,
                                    maxLines = 1
                                )
                            }
                        } else {
                            val leftLine = sideBySideLine.left
                            val bgColor = when (leftLine?.type) {
                                DiffLineType.REMOVED -> extendedColors.diffRemovedBackground
                                else -> Color.Transparent
                            }
                            val textColor = when (leftLine?.type) {
                                DiffLineType.REMOVED -> extendedColors.diffRemovedText
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgColor)
                                    .horizontalScroll(leftScrollState),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Line number gutter
                                Box(
                                    modifier = Modifier
                                        .width(gutterWidth.dp)
                                        .background(gutterBg),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(
                                        text = leftLine?.oldLineNumber?.toString() ?: "",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = gutterTextColor,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                // Content
                                Text(
                                    text = leftLine?.content ?: "",
                                    color = if (leftLine != null) textColor else Color.Transparent,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Center divider — works because parent Row has height(IntrinsicSize.Min)
                Spacer(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(separatorColor)
                )

                // Right pane (new file) — no verticalScroll
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Header
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "  → Modified",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    parsedLines.forEach { sideBySideLine ->
                        if (sideBySideLine.isHunkHeader) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(hunkBg)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = sideBySideLine.hunkText,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = hunkTextColor,
                                    maxLines = 1
                                )
                            }
                        } else {
                            val rightLine = sideBySideLine.right
                            val bgColor = when (rightLine?.type) {
                                DiffLineType.ADDED -> extendedColors.diffAddedBackground
                                else -> Color.Transparent
                            }
                            val textColor = when (rightLine?.type) {
                                DiffLineType.ADDED -> extendedColors.diffAddedText
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgColor)
                                    .horizontalScroll(rightScrollState),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(gutterWidth.dp)
                                        .background(gutterBg),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(
                                        text = rightLine?.newLineNumber?.toString() ?: "",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = gutterTextColor,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = rightLine?.content ?: "",
                                    color = if (rightLine != null) textColor else Color.Transparent,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // === Unified mode (narrow screen) with line numbers ===
            // No verticalScroll — parent LazyColumn handles scrolling
            Column(modifier = Modifier.fillMaxWidth()) {
                parsedLines.forEach { sideBySideLine ->
                    if (sideBySideLine.isHunkHeader) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(hunkBg)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = sideBySideLine.hunkText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = hunkTextColor,
                                maxLines = 1
                            )
                        }
                    } else {
                        // Show removed lines
                        sideBySideLine.left?.let { leftLine ->
                            if (leftLine.type == DiffLineType.REMOVED) {
                                UnifiedDiffLineRow(
                                    oldLineNum = leftLine.oldLineNumber,
                                    newLineNum = null,
                                    content = leftLine.content,
                                    prefix = "-",
                                    bgColor = extendedColors.diffRemovedBackground,
                                    textColor = extendedColors.diffRemovedText,
                                    gutterBg = gutterBg,
                                    gutterTextColor = gutterTextColor,
                                    gutterWidth = gutterWidth
                                )
                            }
                        }
                        // Show added lines
                        sideBySideLine.right?.let { rightLine ->
                            if (rightLine.type == DiffLineType.ADDED) {
                                UnifiedDiffLineRow(
                                    oldLineNum = null,
                                    newLineNum = rightLine.newLineNumber,
                                    content = rightLine.content,
                                    prefix = "+",
                                    bgColor = extendedColors.diffAddedBackground,
                                    textColor = extendedColors.diffAddedText,
                                    gutterBg = gutterBg,
                                    gutterTextColor = gutterTextColor,
                                    gutterWidth = gutterWidth
                                )
                            }
                        }
                        // Context lines (shown once)
                        if (sideBySideLine.left?.type == DiffLineType.CONTEXT) {
                            val ctxLine = sideBySideLine.left
                            UnifiedDiffLineRow(
                                oldLineNum = ctxLine.oldLineNumber,
                                newLineNum = sideBySideLine.right?.newLineNumber,
                                content = ctxLine.content,
                                prefix = " ",
                                bgColor = Color.Transparent,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                gutterBg = gutterBg,
                                gutterTextColor = gutterTextColor,
                                gutterWidth = gutterWidth
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedDiffLineRow(
    oldLineNum: Int?,
    newLineNum: Int?,
    content: String,
    prefix: String,
    bgColor: Color,
    textColor: Color,
    gutterBg: Color,
    gutterTextColor: Color,
    gutterWidth: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Old line number gutter
        Box(
            modifier = Modifier
                .width(gutterWidth.dp)
                .background(gutterBg),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = oldLineNum?.toString() ?: "",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = gutterTextColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        // New line number gutter
        Box(
            modifier = Modifier
                .width(gutterWidth.dp)
                .background(gutterBg),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = newLineNum?.toString() ?: "",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = gutterTextColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        // Prefix indicator (+, -, space)
        Text(
            text = prefix,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
        )
        // Content
        Text(
            text = content,
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            softWrap = false,
            maxLines = 1
        )
    }
}

// ========== Merge UI Components ==========

@Composable
fun MergeBanner(
    conflictCount: Int,
    onAbort: () -> Unit,
    onComplete: () -> Unit
) {
    Surface(
        color = if (conflictCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (conflictCount > 0) Icons.Default.Warning else Icons.Default.MergeType,
                contentDescription = null,
                tint = if (conflictCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Merge in Progress",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (conflictCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (conflictCount > 0) {
                    Text(
                        text = "$conflictCount conflict(s) need resolution",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            TextButton(onClick = onAbort) {
                Text("Abort", color = MaterialTheme.colorScheme.error)
            }
            if (conflictCount == 0) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onComplete) {
                    Text("Complete")
                }
            }
        }
    }
}

@Composable
fun MergeDialog(
    branches: List<BranchInfo>,
    currentBranch: String,
    onMerge: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedBranch by remember { mutableStateOf<BranchInfo?>(null) }
    var fastForwardOnly by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge into $currentBranch") },
        text = {
            Column {
                Text("Select branch to merge:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))

                if (branches.isEmpty()) {
                    Text(
                        "No other branches available to merge",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(branches) { branch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedBranch = branch }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedBranch == branch,
                                    onClick = { selectedBranch = branch }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(branch.name)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { fastForwardOnly = !fastForwardOnly }
                    ) {
                        Checkbox(
                            checked = fastForwardOnly,
                            onCheckedChange = { fastForwardOnly = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Fast-forward only")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedBranch?.let { onMerge(it.name, fastForwardOnly) } },
                enabled = selectedBranch != null
            ) {
                Text("Merge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ========== Remote Management UI ==========

@Composable
fun RemotesDialog(
    remotes: List<RemoteInfo>,
    onAddRemote: (String, String) -> Unit,
    onRemoveRemote: (String) -> Unit,
    onUpdateRemote: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRemote by remember { mutableStateOf<RemoteInfo?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Remotes", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Remote")
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (remotes.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No remotes configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(remotes) { remote ->
                            ListItem(
                                headlineContent = { Text(remote.name, fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Column {
                                        Text("Fetch: ${remote.fetchUrl}", style = MaterialTheme.typography.bodySmall)
                                        if (remote.pushUrl != remote.fetchUrl) {
                                            Text("Push: ${remote.pushUrl}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { editingRemote = remote }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                                        }
                                        IconButton(onClick = { onRemoveRemote(remote.name) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRemoteDialog(
            onAdd = { name, url ->
                onAddRemote(name, url)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingRemote?.let { remote ->
        EditRemoteDialog(
            remote = remote,
            onSave = { url ->
                onUpdateRemote(remote.name, url)
                editingRemote = null
            },
            onDismiss = { editingRemote = null }
        )
    }
}

@Composable
fun AddRemoteDialog(
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Remote") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("origin") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://github.com/user/repo.git") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditRemoteDialog(
    remote: RemoteInfo,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(remote.fetchUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Remote: ${remote.name}") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(url) },
                enabled = url.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ========== Conflict Resolution UI ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolveDialog(
    conflictFile: ConflictFile,
    onResolve: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOption by remember { mutableIntStateOf(-1) } // -1: none, 0: ours, 1: theirs, 2: manual
    var manualContent by remember { mutableStateOf(conflictFile.oursContent) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Resolve: ${conflictFile.path.substringAfterLast("/")}", maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                val content = when (selectedOption) {
                                    0 -> conflictFile.oursContent
                                    1 -> conflictFile.theirsContent
                                    2 -> manualContent
                                    else -> return@Button
                                }
                                onResolve(content)
                            },
                            enabled = selectedOption >= 0
                        ) {
                            Text("Apply")
                        }
                    }
                )

                // Resolution options
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Choose resolution:", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    // Option: Use Ours
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = 0 }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedOption == 0, onClick = { selectedOption = 0 })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Keep Ours (Current branch)", fontWeight = FontWeight.Medium)
                            Text("Keep your version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // Option: Use Theirs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = 1 }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedOption == 1, onClick = { selectedOption = 1 })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Accept Theirs (Incoming)", fontWeight = FontWeight.Medium)
                            Text("Use the incoming version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // Option: Manual Edit
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = 2 }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedOption == 2, onClick = { selectedOption = 2 })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Manual Edit", fontWeight = FontWeight.Medium)
                            Text("Edit the content manually", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                HorizontalDivider()

                // Content preview/edit
                when (selectedOption) {
                    0 -> {
                        Text(
                            "Ours (Current)",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                conflictFile.oursContent,
                                fontFamily = JetBrainsMono,
                                fontSize = 12.sp
                            )
                        }
                    }
                    1 -> {
                        Text(
                            "Theirs (Incoming)",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                conflictFile.theirsContent,
                                fontFamily = JetBrainsMono,
                                fontSize = 12.sp
                            )
                        }
                    }
                    2 -> {
                        Text(
                            "Edit Content",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                        OutlinedTextField(
                            value = manualContent,
                            onValueChange = { manualContent = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp)
                        )
                    }
                    else -> {
                        // Show both versions side by side hint
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Select a resolution option above",
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
