package com.example.roboticgit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import com.example.roboticgit.data.model.FileState
import com.example.roboticgit.data.model.FileStatus
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val rootDir = remember { File(authManager.getDefaultCloneDir()) }

    val viewModel: RepoDetailViewModel = viewModel(
        factory = RepoDetailViewModelFactory(authManager, rootDir, repoName)
    )
    val uiState by viewModel.uiState.collectAsState()
    
    var commitMessage by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    var diffFile by remember { mutableStateOf<String?>(null) }
    var diffText by remember { mutableStateOf<String?>(null) }
    
    var editingPath by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }

    var selectedCommit by remember { mutableStateOf<RevCommit?>(null) }
    var commitChanges by remember { mutableStateOf<List<CommitChange>>(emptyList()) }
    
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(repoName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Changes", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Files", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                    Text("History", modifier = Modifier.padding(16.dp))
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is RepoDetailUiState.Loading -> {
                         CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is RepoDetailUiState.Success -> {
                        when (selectedTab) {
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
                                onCommitClick = { commit ->
                                    selectedCommit = commit
                                    scope.launch {
                                        commitChanges = viewModel.getCommitChanges(commit)
                                    }
                                }
                            )
                        }
                    }
                    is RepoDetailUiState.Error -> {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
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

        if (editingPath != null) {
            EditorDialog(
                fileName = editingPath!!.substringAfterLast("/"),
                content = editingText,
                onContentChange = { editingText = it },
                onSave = {
                    viewModel.saveFile(editingPath!!, editingText)
                    editingPath = null
                },
                onDismiss = { editingPath = null }
            )
        }

        if (selectedCommit != null) {
            CommitDetailDialog(
                commit = selectedCommit!!,
                changes = commitChanges,
                getGravatarUrl = viewModel::getGravatarUrl,
                onFileClick = { path ->
                    diffFile = path
                    scope.launch {
                        diffText = viewModel.getCommitFileDiff(selectedCommit!!, path)
                    }
                },
                onDismiss = {
                    selectedCommit = null
                    commitChanges = emptyList()
                }
            )
        }
    }
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

@Composable
fun ChangesView(
    fileStatuses: List<FileStatus>,
    commitMessage: String,
    onMessageChange: (String) -> Unit,
    onCommit: () -> Unit,
    onToggleStage: (FileStatus) -> Unit,
    onRollback: (FileStatus) -> Unit,
    onFileClick: (FileStatus) -> Unit,
    onEditClick: (FileStatus) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (fileStatuses.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No changes detected")
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(fileStatuses) { fileStatus ->
                    FileStatusItem(
                        fileStatus = fileStatus,
                        onToggleStage = { onToggleStage(fileStatus) },
                        onRollback = { onRollback(fileStatus) },
                        onClick = { onFileClick(fileStatus) },
                        onEditClick = { onEditClick(fileStatus) }
                    )
                    HorizontalDivider()
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = onMessageChange,
                    label = { Text("Commit Message") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onCommit,
                    enabled = commitMessage.isNotBlank() && fileStatuses.any { it.isStaged },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Commit Staged")
                }
            }
        }
    }
}

@Composable
fun FileStatusItem(
    fileStatus: FileStatus,
    onToggleStage: () -> Unit,
    onRollback: () -> Unit,
    onClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val color = when (fileStatus.state) {
        FileState.ADDED, FileState.UNTRACKED -> Color(0xFF4CAF50)
        FileState.MODIFIED -> Color(0xFF2196F3)
        FileState.REMOVED, FileState.DELETED, FileState.MISSING -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurface
    }

    ListItem(
        headlineContent = { Text(fileStatus.path) },
        supportingContent = { 
            Text(fileStatus.state.name, color = color, style = MaterialTheme.typography.bodySmall) 
        },
        leadingContent = {
            Checkbox(
                checked = fileStatus.isStaged,
                onCheckedChange = { onToggleStage() }
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                if (!fileStatus.isStaged && fileStatus.state != FileState.UNTRACKED) {
                    IconButton(onClick = onRollback) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Rollback", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun DiffDialog(
    fileName: String,
    diffText: String?,
    onDismiss: () -> Unit
) {
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
                                line.startsWith("+") -> Color(0xFFE6FFEC)
                                line.startsWith("-") -> Color(0xFFFFEBEE)
                                else -> Color.Transparent
                            }
                            val textColor = when {
                                line.startsWith("+") -> Color(0xFF2E7D32)
                                line.startsWith("-") -> Color(0xFFC62828)
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
    onDismiss: () -> Unit
) {
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
                    title = { Text("Edit: $fileName", maxLines = 1) },
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
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun CodeEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    
    val lineNumbers = remember(value) {
        val count = value.count { it == '\n' } + 1
        (1..count).joinToString("\n")
    }

    val gutterBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val gutterTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Row(modifier = modifier
        .fillMaxSize()
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
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
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
                
                val charWidth = if (layout.lineCount > 0 && layout.getLineEnd(0) > 0) {
                    layout.getHorizontalPosition(1, false)
                } else {
                    7.2.sp.toPx() 
                }
                val tabWidth = charWidth * 4

                for (lineIndex in 0 until layout.lineCount) {
                    val lineStart = layout.getLineStart(lineIndex)
                    val lineEnd = layout.getLineEnd(lineIndex)
                    val lineText = value.substring(lineStart, lineEnd)
                    
                    var leadingSpaces = 0
                    for (char in lineText) {
                        if (char == ' ') leadingSpaces++
                        else if (char == '\t') {
                            leadingSpaces += 4 - (leadingSpaces % 4)
                        } else break
                    }
                    
                    val indentLevels = leadingSpaces / 4
                    if (indentLevels > 0) {
                        val top = layout.getLineTop(lineIndex)
                        val bottom = layout.getLineBottom(lineIndex)
                        
                        for (level in 1..indentLevels) {
                            val x = level * tabWidth
                            drawLine(
                                color = guideColor,
                                start = Offset(x, top),
                                end = Offset(x, bottom),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                onTextLayout = { textLayoutResult = it },
                visualTransformation = WhitespaceVisualTransformation()
            )
        }
    }
}

class WhitespaceVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val out = text.text
            .replace(' ', '·')
            .replace("\t", "»   ")
        
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var transformedOffset = 0
                for (i in 0 until offset) {
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
                return originalOffset
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

@Composable
fun HistoryView(
    commits: List<RevCommit>,
    getGravatarUrl: (String) -> String,
    onCommitClick: (RevCommit) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(commits) { commit ->
            CommitItem(commit, getGravatarUrl, onClick = { onCommitClick(commit) })
            HorizontalDivider()
        }
    }
}

@Composable
fun CommitItem(
    commit: RevCommit,
    getGravatarUrl: (String) -> String,
    onClick: () -> Unit
) {
    val email = commit.authorIdent.emailAddress
    ListItem(
        headlineContent = { Text(commit.shortMessage) },
        supportingContent = { 
            Text("${commit.authorIdent.name} - ${Date(commit.commitTime * 1000L)}") 
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
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun CommitDetailDialog(
    commit: RevCommit,
    changes: List<CommitChange>,
    getGravatarUrl: (String) -> String,
    onFileClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Commit Details") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = getGravatarUrl(commit.authorIdent.emailAddress),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(commit.authorIdent.name, style = MaterialTheme.typography.titleMedium)
                        Text(Date(commit.commitTime * 1000L).toString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Hash: ${commit.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(8.dp))
                Text(commit.fullMessage, style = MaterialTheme.typography.bodyMedium)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Changes", style = MaterialTheme.typography.titleSmall)
                changes.forEach { change ->
                    ListItem(
                        headlineContent = { Text(change.path, fontSize = 14.sp) },
                        supportingContent = { Text(change.changeType, style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.clickable { onFileClick(change.path) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
