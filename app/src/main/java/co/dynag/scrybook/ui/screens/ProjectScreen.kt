package co.dynag.scrybook.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import co.dynag.scrybook.R
import co.dynag.scrybook.data.model.Chapitre
import co.dynag.scrybook.data.model.Info
import co.dynag.scrybook.ui.viewmodel.ExportViewModel
import co.dynag.scrybook.ui.viewmodel.ExportResult
import co.dynag.scrybook.ui.viewmodel.ProjectViewModel
import co.dynag.scrybook.ui.components.ProjectDrawerContent
import co.dynag.scrybook.ui.components.SummaryPanel
import co.dynag.scrybook.ui.components.ScryBookMarkdown
import co.dynag.scrybook.ui.components.MarkdownTextField
import kotlinx.coroutines.launch
import co.dynag.scrybook.ui.components.ScryBookBottomBar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    projectPath: String,
    onChapterOpen: (Long) -> Unit,
    onInfoOpen: () -> Unit,
    onCharactersOpen: () -> Unit,
    onPlacesOpen: () -> Unit,
    onSettingsOpen: () -> Unit,
    onFullSummaryOpen: () -> Unit,
    onBack: () -> Unit,
    viewModel: ProjectViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel = hiltViewModel()
) {
    val chapitres by viewModel.chapitres.collectAsState()
    val info by viewModel.info.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showNewChapterDialog by viewModel.showNewChapterDialog.collectAsState()
    val exporting by exportViewModel.exporting.collectAsState()
    val exportResult by exportViewModel.result.collectAsState()

    var newChapNom by remember { mutableStateOf("") }
    var newChapNum by remember { mutableStateOf("") }
    var newChapResume by remember { mutableStateOf("") }
    var chapterToDelete by remember { mutableStateOf<Chapitre?>(null) }
    var chapterToEdit by remember { mutableStateOf<Chapitre?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            exportViewModel.exportBookPdf(projectPath, it)
        }
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val showPermanentUI = isLandscape || isTablet

    LaunchedEffect(projectPath) { viewModel.loadProject(projectPath) }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            onBack()
        }
    }

    LaunchedEffect(exportResult) {
        exportResult?.let { result ->
            when (result) {
                is ExportResult.Success -> snackbarHostState.showSnackbar("PDF exporté: ${result.path}")
                is ExportResult.Error -> snackbarHostState.showSnackbar("Erreur: ${result.message}")
            }
            exportViewModel.clearResult()
        }
    }

    if (showPermanentUI) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    modifier = Modifier.width(250.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    ProjectDrawerContent(
                        chapitres = chapitres,
                        onChapterOpen = { id -> onChapterOpen(id) },
                        onNewChapter = { viewModel.showNewChapterDialog() },
                        onHeaderClick = { onBack() },
                        onTitleClick = { _ -> /* No specific anchor navigation here yet, just open chapter */ }
                    )
                }
            }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    ProjectMainContent(
                        info = info,
                        projectPath = projectPath,
                        chapitres = chapitres,
                        isLoading = isLoading,
                        exporting = exporting,
                        snackbarHostState = snackbarHostState,
                        isLandscape = true,
                        onBack = onBack,
                        onDrawerOpen = { scope.launch { drawerState.open() } },
                        onExport = {
                            val fileName = (info.titre.ifBlank { File(projectPath).nameWithoutExtension }) + ".pdf"
                            exportLauncher.launch(fileName)
                        },
                        onSettingsOpen = onSettingsOpen,
                        onNewChapter = { viewModel.showNewChapterDialog() },
                        onCharactersOpen = onCharactersOpen,
                        onPlacesOpen = onPlacesOpen,
                        onFullSummaryOpen = onFullSummaryOpen,
                        onInfoOpen = onInfoOpen,
                        onChapterOpen = onChapterOpen,
                        onChapterEdit = { chapterToEdit = it },
                        onChapterDelete = { chapterToDelete = it }
                    )
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SummaryPanel(
                    title = stringResource(R.string.nav_summary),
                    resume = info.resume,
                    modifier = Modifier.width(300.dp),
                    onSave = { newResume ->
                        viewModel.updateProjectResume(newResume)
                    }
                )
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.85f)) {
                    ProjectDrawerContent(
                        chapitres = chapitres,
                        onChapterOpen = { id ->
                            scope.launch { drawerState.close() }
                            onChapterOpen(id)
                        },
                        onNewChapter = {
                            scope.launch { drawerState.close() }
                            viewModel.showNewChapterDialog()
                        },
                        onHeaderClick = {
                            scope.launch { drawerState.close() }
                            onBack()
                        }
                    )
                }
            }
        ) {
            ProjectMainContent(
                info = info,
                projectPath = projectPath,
                chapitres = chapitres,
                isLoading = isLoading,
                exporting = exporting,
                snackbarHostState = snackbarHostState,
                isLandscape = false,
                onBack = onBack,
                onDrawerOpen = { scope.launch { drawerState.open() } },
                onExport = {
                    val fileName = (info.titre.ifBlank { File(projectPath).nameWithoutExtension }) + ".pdf"
                    exportLauncher.launch(fileName)
                },
                onSettingsOpen = onSettingsOpen,
                onNewChapter = { viewModel.showNewChapterDialog() },
                onCharactersOpen = onCharactersOpen,
                onPlacesOpen = onPlacesOpen,
                onFullSummaryOpen = onFullSummaryOpen,
                onInfoOpen = onInfoOpen,
                onChapterOpen = onChapterOpen,
                onChapterEdit = { chapterToEdit = it },
                onChapterDelete = { chapterToDelete = it }
            )
        }
    }

    // New chapter dialog
    if (showNewChapterDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissNewChapterDialog() },
            title = { Text(stringResource(R.string.action_new_chapter)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(newChapNom, { newChapNom = it }, label = { Text(stringResource(R.string.chapter_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newChapNum, { newChapNum = it }, label = { Text(stringResource(R.string.chapter_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    MarkdownTextField(
                        value = newChapResume,
                        onValueChange = { newChapResume = it },
                        label = stringResource(R.string.chapter_summary),
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newChapNom.isNotBlank()) {
                        viewModel.addChapitre(newChapNom, newChapNum, newChapResume)
                        newChapNom = ""; newChapNum = ""; newChapResume = ""
                    }
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissNewChapterDialog() }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    // Edit chapter dialog
    chapterToEdit?.let { ch ->
        var editNom by remember(ch.id) { mutableStateOf(ch.nom) }
        var editNum by remember(ch.id) { mutableStateOf(ch.numero) }
        var editResume by remember(ch.id) { mutableStateOf(ch.resume) }
        AlertDialog(
            onDismissRequest = { chapterToEdit = null },
            title = { Text(stringResource(R.string.chapter_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(editNom, { editNom = it }, label = { Text(stringResource(R.string.chapter_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editNum, { editNum = it }, label = { Text(stringResource(R.string.chapter_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    MarkdownTextField(
                        value = editResume,
                        onValueChange = { editResume = it },
                        label = stringResource(R.string.chapter_summary),
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.updateChapitreInfo(ch.id, editNom, editNum, editResume); chapterToEdit = null }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = { TextButton(onClick = { chapterToEdit = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    // Delete confirmation
    chapterToDelete?.let { ch ->
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.chapter_delete_title)) },
            text = { Text(stringResource(R.string.chapter_delete_confirm, ch.nom)) },
            confirmButton = {
                Button(onClick = { viewModel.deleteChapitre(ch.id); chapterToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { chapterToDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectMainContent(
    info: Info,
    projectPath: String,
    chapitres: List<Chapitre>,
    isLoading: Boolean,
    exporting: Boolean,
    snackbarHostState: SnackbarHostState,
    isLandscape: Boolean,
    onBack: () -> Unit,
    onDrawerOpen: () -> Unit,
    onExport: () -> Unit,
    onSettingsOpen: () -> Unit,
    onNewChapter: () -> Unit,
    onCharactersOpen: () -> Unit,
    onPlacesOpen: () -> Unit,
    onFullSummaryOpen: () -> Unit,
    onInfoOpen: () -> Unit,
    onChapterOpen: (Long) -> Unit,
    onChapterEdit: (Chapitre) -> Unit,
    onChapterDelete: (Chapitre) -> Unit
) {
    val isEtude = projectPath.endsWith(".sbe")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    Column {
                        Text(
                            info.titre.ifBlank { File(projectPath).nameWithoutExtension },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (info.auteur.isNotBlank()) {
                            Text(info.auteur, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    // Chapters drawer toggle - hidden in landscape
                    if (!isLandscape) {
                        IconButton(onClick = onDrawerOpen) {
                            BadgedBox(badge = {
                                if (chapitres.isNotEmpty()) Badge { Text("${chapitres.size}") }
                            }) {
                                Icon(Icons.Default.MenuBook, contentDescription = stringResource(R.string.drawer_chapters))
                            }
                        }
                    }
                    // Export PDF
                    IconButton(
                        onClick = onExport,
                        enabled = !exporting
                    ) {
                        if (exporting) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PictureAsPdf, contentDescription = stringResource(R.string.action_export_pdf))
                    }
                    // Settings
                    IconButton(onClick = onSettingsOpen) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = { ScryBookBottomBar() },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChapter,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_new_chapter))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Quick navigation chips — scrollable horizontally
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isEtude) {
                    item {
                        FilterChip(false, onClick = onCharactersOpen,
                            label = { Text(stringResource(R.string.nav_characters), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(16.dp)) })
                    }
                }
                item {
                    val labelText = if (isEtude) "Sites" else stringResource(R.string.nav_places)
                    val icon = if (isEtude) Icons.Default.Language else Icons.Default.Place
                    FilterChip(false, onClick = onPlacesOpen,
                        label = { Text(labelText, maxLines = 1) },
                        leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) })
                }
                item {
                    FilterChip(false, onClick = onFullSummaryOpen,
                        label = { Text(stringResource(R.string.nav_summary), maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Summarize, null, Modifier.size(16.dp)) })
                }
                item {
                    FilterChip(false, onClick = onInfoOpen,
                        label = { Text(stringResource(R.string.nav_info), maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Info, null, Modifier.size(16.dp)) })
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (chapitres.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Book, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text(stringResource(R.string.project_no_chapters), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chapitres, key = { it.id }) { chapitre ->
                        ChapitreCard(chapitre, onClick = { onChapterOpen(chapitre.id) }, onEdit = { onChapterEdit(chapitre) }, onDelete = { onChapterDelete(chapitre) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapitreCard(chapitre: Chapitre, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(chapitre.numero.ifBlank { "—" }, style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(chapitre.nom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chapitre.resume.isNotBlank()) {
                    ScryBookMarkdown(
                        content = chapitre.resume,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)) }
        }
    }
}
