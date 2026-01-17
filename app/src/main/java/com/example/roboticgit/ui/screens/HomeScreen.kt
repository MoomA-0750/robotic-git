package com.example.roboticgit.ui.screens

import android.graphics.Path
import android.text.format.DateUtils
import android.view.animation.PathInterpolator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roboticgit.data.AuthManager
import com.example.roboticgit.data.model.Account
import com.example.roboticgit.data.model.GitRepo
import com.example.roboticgit.data.model.RemoteRepo
import com.example.roboticgit.ui.viewmodel.HomeUiState
import com.example.roboticgit.ui.viewmodel.HomeViewModel
import com.example.roboticgit.ui.viewmodel.HomeViewModelFactory

// Pre-created easing to avoid jank on first animation
// Path: M 0,0 C 0.05, 0, 0.133333, 0.06, 0.166666, 0.4 C 0.208333, 0.82, 0.25, 1, 1, 1
private val EmphasizedEasing: Easing = Path().let { path ->
    path.moveTo(0f, 0f)
    path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
    path.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    val interpolator = PathInterpolator(path)
    Easing { fraction -> interpolator.getInterpolation(fraction) }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    // Refresh accounts when screen is shown (in case of external changes)
    LaunchedEffect(Unit) {
        viewModel.refreshAccounts()
    }

    val repos by viewModel.repos.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val remoteRepos by viewModel.remoteRepos.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedRepos by viewModel.selectedRepos.collectAsState()
    
    val isSelectionMode = selectedRepos.isNotEmpty()

    var showCloneImportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDialogAnimating by remember { mutableStateOf(false) }

    // FAB position tracking for container transform animation
    var fabBounds by remember { mutableStateOf(Rect.Zero) }

    BackHandler(enabled = isSelectionMode || showCloneImportDialog) {
        if (showCloneImportDialog) {
            showCloneImportDialog = false
        } else {
            viewModel.clearSelection()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (isSelectionMode) {
                    SelectionTopBar(
                        selectedCount = selectedRepos.size,
                        onClearSelection = { viewModel.clearSelection() },
                        onFetch = { viewModel.refreshSelectedRepositories(selectedRepos) },
                        onPull = { viewModel.pullSelectedRepositories() },
                        onDelete = { showDeleteConfirm = true }
                    )
                }
            },
            floatingActionButton = {
                if (!isSelectionMode && !showCloneImportDialog && !isDialogAnimating) {
                    ExtendedFloatingActionButton(
                        onClick = { showCloneImportDialog = true },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Add Repository") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            fabBounds = coordinates.boundsInRoot()
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshAllRepositories() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (val state = uiState) {
                        is HomeUiState.Loading -> {
                            if (!isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                        }
                        is HomeUiState.Success -> {
                            if (repos.isEmpty()) {
                                EmptyState(modifier = Modifier.align(Alignment.Center))
                            } else {
                                RepoList(
                                    repos = repos,
                                    selectedRepos = selectedRepos,
                                    isSelectionMode = isSelectionMode,
                                    onRepoClick = { repoName ->
                                        if (isSelectionMode) {
                                            viewModel.toggleRepoSelection(repoName)
                                        } else {
                                            onRepoClick(repoName)
                                        }
                                    },
                                    onRepoLongClick = { repoName ->
                                        viewModel.toggleRepoSelection(repoName)
                                    }
                                )
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

        // Container Transform Animation
        val fabContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ContainerTransformDialog(
            visible = showCloneImportDialog,
            fabBounds = fabBounds,
            fabColor = MaterialTheme.colorScheme.primaryContainer,
            dialogColor = MaterialTheme.colorScheme.surface,
            onAnimatingChanged = { isDialogAnimating = it },
            fabContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = fabContentColor
                    )
                    Text(
                        "Add Repository",
                        color = fabContentColor,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        ) {
            CloneImportFullScreenDialog(
                accounts = accounts,
                selectedAccount = selectedAccount,
                remoteRepos = remoteRepos,
                onAccountSelect = { viewModel.selectAccount(it) },
                onDismiss = { showCloneImportDialog = false },
                onClone = { url, name ->
                    viewModel.cloneRepository(url, name)
                    showCloneImportDialog = false
                },
                onImport = { repo ->
                    viewModel.cloneRepository(repo.cloneUrl, repo.name)
                    showCloneImportDialog = false
                }
            )
        }
    }

    if (showDeleteConfirm) {
        ModernHomeAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Delete Repositories",
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedRepositories()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        ) {
            Text("Are you sure you want to delete ${selectedRepos.size} selected repositories from your device? This action cannot be undone.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneImportFullScreenDialog(
    accounts: List<Account>,
    selectedAccount: Account?,
    remoteRepos: List<RemoteRepo>,
    onAccountSelect: (Account) -> Unit,
    onDismiss: () -> Unit,
    onClone: (String, String) -> Unit,
    onImport: (RemoteRepo) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var manualUrl by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Add Repository") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Import") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Clone") })
            }

            when (tabIndex) {
                0 -> {
                    ImportTabContent(
                        accounts = accounts,
                        selectedAccount = selectedAccount,
                        repos = remoteRepos,
                        onAccountSelect = onAccountSelect,
                        onImport = onImport
                    )
                }
                1 -> {
                    CloneTabContent(
                        url = manualUrl,
                        name = manualName,
                        onUrlChange = { manualUrl = it },
                        onNameChange = { manualName = it },
                        onClone = { onClone(manualUrl, manualName) }
                    )
                }
            }
        }
    }
}

@Composable
fun ImportTabContent(
    accounts: List<Account>,
    selectedAccount: Account?,
    repos: List<RemoteRepo>,
    onAccountSelect: (Account) -> Unit,
    onImport: (RemoteRepo) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Account Selector
        if (accounts.isNotEmpty()) {
            Text(
                "Account",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                items(accounts) { account ->
                    ListItem(
                        headlineContent = { Text(account.name) },
                        leadingContent = {
                            RadioButton(
                                selected = account.id == selectedAccount?.id,
                                onClick = { onAccountSelect(account) }
                            )
                        },
                        modifier = Modifier.clickable { onAccountSelect(account) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        Text(
            "Remote Repositories",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        if (repos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No repositories found in this account.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                itemsIndexed(repos) { index, repo ->
                    ListItem(
                        headlineContent = { Text(repo.fullName) },
                        supportingContent = { Text(if (repo.private) "Private" else "Public") },
                        modifier = Modifier.clickable { onImport(repo) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (index < repos.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CloneTabContent(
    url: String,
    name: String,
    onUrlChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onClone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Repository URL") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Local Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Button(
            onClick = onClone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = url.isNotBlank() && name.isNotBlank()
        ) {
            Text("Start Clone")
        }
    }
}

@Composable
fun ModernHomeAlertDialog(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onFetch: () -> Unit,
    onPull: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        TopAppBar(
            title = { Text("$selectedCount selected") },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Clear selection")
                }
            },
            actions = {
                IconButton(onClick = onFetch) {
                    Icon(Icons.Default.Refresh, contentDescription = "Fetch selected")
                }
                IconButton(onClick = onPull) {
                    Icon(Icons.Default.Download, contentDescription = "Pull selected")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            windowInsets = WindowInsets.statusBars
        )
    }
}

@Composable
fun RepoList(
    repos: List<GitRepo>,
    selectedRepos: Set<String>,
    isSelectionMode: Boolean,
    onRepoClick: (String) -> Unit,
    onRepoLongClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Repositories",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(bottom = 8.dp)
            )
        }
        items(repos, key = { it.name }) { repo ->
            RepoItem(
                repo = repo,
                isSelected = selectedRepos.contains(repo.name),
                onClick = { onRepoClick(repo.name) },
                onLongClick = { onRepoLongClick(repo.name) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RepoItem(
    repo: GitRepo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val relativeTime = if (repo.lastCommitTime > 0) {
        DateUtils.getRelativeTimeSpanString(
            repo.lastCommitTime,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    } else {
        ""
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "repoItemBackgroundColor"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = !repo.isCloning
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                               else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = repo.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        if (relativeTime.isNotEmpty() && !repo.isCloning) {
                            Text(
                                text = relativeTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = if (repo.isCloning) "Cloning now..." else if (repo.lastCommitTime > 0) "Last committed" else "No commits yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (repo.isCloning) {
                Column {
                    Text(
                        text = "Status: ${repo.statusMessage}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = repo.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                }
            } else {
                Text(
                    text = repo.path,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No repositories found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Tap the Clone button to start",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ContainerTransformDialog(
    visible: Boolean,
    fabBounds: Rect,
    fabColor: Color,
    dialogColor: Color,
    onAnimatingChanged: (Boolean) -> Unit,
    fabContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current

    // Remember the last valid FAB bounds for closing animation
    var lastValidFabBounds by remember { mutableStateOf(fabBounds) }
    if (fabBounds != Rect.Zero) {
        lastValidFabBounds = fabBounds
    }

    // Animation progress (0f = FAB, 1f = Full screen)
    val animationProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = EmphasizedEasing),
        label = "containerTransformProgress"
    )

    // Notify parent about animation state
    LaunchedEffect(animationProgress) {
        onAnimatingChanged(animationProgress > 0f)
    }

    // Only show when animating or visible
    if (animationProgress > 0f) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = constraints.maxWidth.toFloat()
            val screenHeight = constraints.maxHeight.toFloat()

            // Use last valid bounds or fallback to bottom-right corner
            val effectiveBounds = if (lastValidFabBounds != Rect.Zero) {
                lastValidFabBounds
            } else {
                // Fallback: bottom-right corner, typical FAB position
                Rect(
                    left = screenWidth - 200f,
                    top = screenHeight - 120f,
                    right = screenWidth - 16f,
                    bottom = screenHeight - 16f
                )
            }

            // Calculate interpolated values
            val fabCenterX = effectiveBounds.center.x
            val fabCenterY = effectiveBounds.center.y
            val fabWidth = effectiveBounds.width.coerceAtLeast(56f)
            val fabHeight = effectiveBounds.height.coerceAtLeast(56f)

            // Interpolate size
            val currentWidth = lerp(fabWidth, screenWidth, animationProgress)
            val currentHeight = lerp(fabHeight, screenHeight, animationProgress)

            // Interpolate position (from FAB center to screen center)
            val screenCenterX = screenWidth / 2
            val screenCenterY = screenHeight / 2
            val currentCenterX = lerp(fabCenterX, screenCenterX, animationProgress)
            val currentCenterY = lerp(fabCenterY, screenCenterY, animationProgress)

            // Calculate offset from top-left
            val offsetX = currentCenterX - currentWidth / 2
            val offsetY = currentCenterY - currentHeight / 2

            // Interpolate corner radius (16dp for FAB, 0 for full screen)
            val cornerRadius = lerp(16f, 0f, animationProgress)

            // Interpolate color
            val currentColor = lerp(fabColor, dialogColor, animationProgress)

            // Dialog content alpha (fade in after expansion, fade out early when closing)
            val dialogContentAlpha = if (visible) {
                ((animationProgress - 0.2f) / 0.5f).coerceIn(0f, 1f)
            } else {
                ((animationProgress - 0.3f) / 0.4f).coerceIn(0f, 1f)
            }

            // FAB content alpha (show when closing and nearly complete)
            val fabContentAlpha = if (visible) {
                0f
            } else {
                (1f - animationProgress * 2.5f).coerceIn(0f, 1f)
            }

            Surface(
                modifier = Modifier
                    .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                    .width(with(density) { currentWidth.toDp() })
                    .height(with(density) { currentHeight.toDp() }),
                shape = RoundedCornerShape(cornerRadius.dp),
                color = currentColor,
                shadowElevation = lerp(6f, 0f, animationProgress).dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Dialog content
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = dialogContentAlpha }
                    ) {
                        content()
                    }
                    // FAB content (shown when closing)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = fabContentAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        fabContent()
                    }
                }
            }
        }
    }
}

// Linear interpolation helper
private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = lerp(start.red, stop.red, fraction),
        green = lerp(start.green, stop.green, fraction),
        blue = lerp(start.blue, stop.blue, fraction),
        alpha = lerp(start.alpha, stop.alpha, fraction)
    )
}
