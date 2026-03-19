package co.dynag.scrybook.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.os.Build
import androidx.hilt.navigation.compose.hiltViewModel
import co.dynag.scrybook.R
import co.dynag.scrybook.ui.viewmodel.HomeViewModel
import co.dynag.scrybook.ui.components.ScryBookBottomBar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onProjectOpen: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val projects by viewModel.projects.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var newProjectDir by remember { mutableStateOf("") }
    var isEtudeModeEnabled by remember { mutableStateOf(viewModel.isEtudeModeEnabled()) }

    LaunchedEffect(Unit) { newProjectDir = viewModel.defaultProjectDir() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }

    // Permission logic
    var showPermissionRationale by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val hasPermission = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11+ doesn't need READ_EXT if using its own dir + SAF
            true 
        }
        
        if (!hasPermission && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            showPermissionRationale = true
        } else {
            viewModel.scanForProjects()
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            // Attempt to resolve real path or copy to persistent storage
            try {
                val finalPath: String
                
                // Try to get direct path
                val directFile = viewModel.getRealPathFromUri(uri)?.let { java.io.File(it) }
                if (directFile != null && directFile.exists() && directFile.canWrite()) {
                    finalPath = directFile.absolutePath
                } else {
                    val destDir = java.io.File(viewModel.defaultProjectDir())
                    destDir.mkdirs()
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "project.sb"
                    val destFile = java.io.File(destDir, fileName)
                    finalPath = destFile.absolutePath

                    // Always refresh from original
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                // Take persistable permission if possible
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) { /* Not supported by all providers */ }

                viewModel.addToRecent(finalPath, uri.toString())
                onProjectOpen(finalPath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Directory picker launcher
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Attempt to resolve real path from tree URI or just use a message
            // DocumentTree URIs are hard to convert to absolute path.
            // But we can try to guess or use a custom resolver.
            // For now, let's just show a snackbar or update the field if we can.
            // On many devices, it will return content://.../document/primary:Documents for example.
            val decoded = Uri.decode(it.toString())
            if (decoded.contains("primary:")) {
                val subPath = decoded.substringAfter("primary:")
                newProjectDir = File(Environment.getExternalStorageDirectory(), subPath).absolutePath
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    // Open file button
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        filePickerLauncher.launch(intent)
                    }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.action_open_file))
                    }
                    IconButton(onClick = { viewModel.scanForProjects() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Plus d'options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isEtudeModeEnabled) "Mode Étude activé" else "Activer Mode Étude") },
                                onClick = {
                                    val newVal = !isEtudeModeEnabled
                                    viewModel.setEtudeModeEnabled(newVal)
                                    isEtudeModeEnabled = newVal
                                    showMenu = false
                                },
                                leadingIcon = { 
                                    Icon(
                                        Icons.Default.School, 
                                        contentDescription = null,
                                        tint = if (isEtudeModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = { ScryBookBottomBar() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_project)) },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                projects.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text(stringResource(R.string.home_no_projects), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showCreateDialog = true }) { Text(stringResource(R.string.action_new_project)) }
                            OutlinedButton(onClick = {
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
                                filePickerLauncher.launch(intent)
                            }) {
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.action_open_file))
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(projects) { project ->
                            ProjectCard(project.name, project.originalUri ?: project.path, viewModel.formatDate(project.lastModified), onClick = { 
                                viewModel.addToRecent(project.path, project.originalUri)
                                onProjectOpen(project.path) 
                            })
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var isEtudeState by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
            title = { Text(stringResource(R.string.action_new_project)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(newProjectName, { newProjectName = it }, label = { Text(stringResource(R.string.project_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newProjectDir, { newProjectDir = it }, label = { Text(stringResource(R.string.project_folder)) }, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { directoryPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Parcourir")
                            }
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = isEtudeState, onCheckedChange = { isEtudeState = it })
                        Text("Créer comme étude (Fichier .sbe)")
                    }
                    TextButton(onClick = { newProjectDir = viewModel.defaultProjectDir() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FolderSpecial, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.project_use_default_folder), style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newProjectName.isNotBlank()) {
                        val path = viewModel.createProject(newProjectDir, newProjectName, isEtudeState)
                        if (path != null) { showCreateDialog = false; newProjectName = ""; onProjectOpen(path) }
                    }
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(R.string.perm_storage_title)) },
            text = { Text(stringResource(R.string.perm_storage_desc)) },
            confirmButton = {
                Button(onClick = {
                    showPermissionRationale = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }) { Text(stringResource(R.string.perm_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProjectCard(name: String, path: String, lastModified: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(lastModified, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
