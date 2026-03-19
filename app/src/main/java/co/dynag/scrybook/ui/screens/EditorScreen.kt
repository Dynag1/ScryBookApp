package co.dynag.scrybook.ui.screens

import android.annotation.SuppressLint
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import co.dynag.scrybook.R
import co.dynag.scrybook.ui.components.ProjectDrawerContent
import co.dynag.scrybook.ui.components.SummaryPanel
import co.dynag.scrybook.ui.components.ScryBookBottomBar
import co.dynag.scrybook.ui.components.MarkdownTextField
import co.dynag.scrybook.ui.viewmodel.EditorViewModel
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

enum class SidePanelType {
    SUMMARY, CHARACTERS, PLACES
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectPath: String,
    chapterId: Long,
    onBack: () -> Unit,
    onChapterOpen: ((Long) -> Unit)? = null,
    onCharactersOpen: (() -> Unit)? = null,
    onPlacesOpen: (() -> Unit)? = null,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val chapitre by viewModel.chapitre.collectAsState()
    val chapitres by viewModel.chapitres.collectAsState()
    val htmlContent by viewModel.htmlContent.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()
    val ttsReady by viewModel.ttsReady.collectAsState()
    val param by viewModel.param.collectAsState()

    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isSttListening by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("scrybook_recent", android.content.Context.MODE_PRIVATE) }
    val isEtudeMode = prefs.getBoolean("mode_etude", false)
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    
    var showNewChapterDialog by remember { mutableStateOf(false) }
    var showEditChapterDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    var activeSidePanel by remember { mutableStateOf(SidePanelType.SUMMARY) }

    val configuration = LocalConfiguration.current
    // Detection logic for permanent menu: Landscape OR Tablet (> 8 inches / sw600dp)
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val showPermanentUI = isLandscape || isTablet

    // Launcher for image picker — resize then encode to Base64 and inject as data URI
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { selectedUri ->
            try {
                // 1. Décoder le bitmap depuis le flux
                val original = context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
                if (original != null) {
                    // 2. Redimensionner si la largeur dépasse 1200px (proportionnel)
                    val maxWidth = 1200
                    val resized = if (original.width > maxWidth) {
                        val ratio = maxWidth.toFloat() / original.width.toFloat()
                        val newHeight = (original.height * ratio).toInt()
                        android.graphics.Bitmap.createScaledBitmap(original, maxWidth, newHeight, true)
                            .also { if (it !== original) original.recycle() }
                    } else {
                        original
                    }
                    // 3. Compresser en JPEG qualité 85 (comme l'appli bureau)
                    val out = java.io.ByteArrayOutputStream()
                    resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    resized.recycle()
                    // 4. Encoder en Base64 et injecter comme data URI
                    val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                    val dataUri = "data:image/jpeg;base64,$base64"
                    val escaped = dataUri.replace("\\", "\\\\").replace("'", "\\'")
                    webView?.evaluateJavascript("insertImage('$escaped');", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(projectPath, chapterId) {
        viewModel.loadChapitre(projectPath, chapterId)
    }

    BackHandler {
        viewModel.saveNow()
        onBack()
    }

    // Save on back
    DisposableEffect(Unit) {
        onDispose { viewModel.saveNow() }
    }

    // Save on pause/stop (app close or background)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.saveNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                        onChapterOpen = { id -> 
                            viewModel.saveNow()
                            onChapterOpen?.invoke(id) 
                        },
                        onNewChapter = { showNewChapterDialog = true },
                        selectedId = chapterId,
                        onHeaderClick = { viewModel.saveNow(); onBack() },
                        onTitleClick = { title ->
                            val escaped = title.replace("'", "\\'")
                            webView?.evaluateJavascript("scrollToTitle('$escaped');", null)
                        }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    EditorTopAppBar(
                        chapitreNom = chapitre?.nom ?: "",
                        isSaving = isSaving,
                        onBack = { viewModel.saveNow(); onBack() },
                        onCharactersOpen = { activeSidePanel = SidePanelType.CHARACTERS },
                        onPlacesOpen = { activeSidePanel = SidePanelType.PLACES },
                        onToggleTts = { viewModel.toggleTts(htmlContent) },
                        isTtsPlaying = isTtsPlaying,
                        ttsReady = ttsReady,
                        isSttListening = isSttListening,
                        onToggleStt = {
                            if (isSttListening) {
                                speechRecognizer?.stopListening()
                                isSttListening = false
                            } else {
                                val sr = SpeechRecognizer.createSpeechRecognizer(context)
                                speechRecognizer = sr
                                sr.setRecognitionListener(object : RecognitionListener {
                                    override fun onResults(results: android.os.Bundle?) {
                                        isSttListening = false
                                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        if (!matches.isNullOrEmpty()) {
                                            val text = matches[0]
                                            webView?.evaluateJavascript("insertTextAtCursor('${text.replace("'", "\\'")}');", null)
                                        }
                                    }
                                    override fun onReadyForSpeech(p: android.os.Bundle?) { isSttListening = true }
                                    override fun onError(error: Int) { isSttListening = false }
                                    override fun onEndOfSpeech() {}
                                    override fun onBeginningOfSpeech() {}
                                    override fun onRmsChanged(v: Float) {}
                                    override fun onBufferReceived(b: ByteArray?) {}
                                    override fun onPartialResults(b: android.os.Bundle?) {}
                                    override fun onEvent(t: Int, b: android.os.Bundle?) {}
                                })
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH.toLanguageTag())
                                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                }
                                sr.startListening(intent)
                            }
                        },
                        onSave = { viewModel.saveNow() },
                        onDelete = { showDeleteConfirmDialog = true },
                        onEditMetadata = { showEditChapterDialog = true },
                        onSaveAsTemplate = if (isEtudeMode) { {
                            viewModel.saveAsTemplate()
                            android.widget.Toast.makeText(context, "Modèle enregistré !", android.widget.Toast.LENGTH_SHORT).show()
                        } } else null
                    )
                },
                bottomBar = { ScryBookBottomBar() }
            ) { innerPadding ->
                // Key the content on chapterId to force recreation when switching chapters
                key(chapterId) {
                    Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        Box(modifier = Modifier.weight(1f)) {
                            EditorMainContent(
                                webView = webView,
                                onWebViewCreated = { webView = it },
                                htmlContent = htmlContent,
                                onContentChanged = { viewModel.updateContent(it) },
                                onInsertImage = { imagePickerLauncher.launch("image/*") },
                                fontSize = param.taille
                            )
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(modifier = Modifier.width(300.dp)) {
                            when (activeSidePanel) {
                                SidePanelType.SUMMARY -> {
                                    SummaryPanel(
                                        title = stringResource(R.string.chapter_summary),
                                        resume = chapitre?.resume ?: "",
                                        modifier = Modifier.fillMaxSize(),
                                        onSave = { newResume ->
                                            chapitre?.let {
                                                viewModel.updateChapitreInfo(it.id, it.nom, it.numero, newResume)
                                            }
                                        }
                                    )
                                }
                                SidePanelType.CHARACTERS -> {
                                    CharactersSidePanel(
                                        projectPath = projectPath,
                                        onClose = { activeSidePanel = SidePanelType.SUMMARY }
                                    )
                                }
                                SidePanelType.PLACES -> {
                                    PlacesSidePanel(
                                        projectPath = projectPath,
                                        onClose = { activeSidePanel = SidePanelType.SUMMARY }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                EditorTopAppBar(
                    chapitreNom = chapitre?.nom ?: "",
                    isSaving = isSaving,
                    onBack = { viewModel.saveNow(); onBack() },
                    onCharactersOpen = onCharactersOpen,
                    onPlacesOpen = onPlacesOpen,
                    onToggleTts = { viewModel.toggleTts(htmlContent) },
                    isTtsPlaying = isTtsPlaying,
                    ttsReady = ttsReady,
                    isSttListening = isSttListening,
                    onToggleStt = {
                        if (isSttListening) {
                            speechRecognizer?.stopListening()
                            isSttListening = false
                        } else {
                            val sr = SpeechRecognizer.createSpeechRecognizer(context)
                            speechRecognizer = sr
                            sr.setRecognitionListener(object : RecognitionListener {
                                override fun onResults(results: android.os.Bundle?) {
                                    isSttListening = false
                                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    if (!matches.isNullOrEmpty()) {
                                        val text = matches[0]
                                        webView?.evaluateJavascript("insertTextAtCursor('${text.replace("'", "\\'")}');", null)
                                    }
                                }
                                override fun onReadyForSpeech(p: android.os.Bundle?) { isSttListening = true }
                                override fun onError(error: Int) { isSttListening = false }
                                override fun onEndOfSpeech() {}
                                override fun onBeginningOfSpeech() {}
                                override fun onRmsChanged(v: Float) {}
                                override fun onBufferReceived(b: ByteArray?) {}
                                override fun onPartialResults(b: android.os.Bundle?) {}
                                override fun onEvent(t: Int, b: android.os.Bundle?) {}
                            })
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH.toLanguageTag())
                                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            }
                            sr.startListening(intent)
                        }
                    },
                    onSave = { viewModel.saveNow() },
                    onDelete = { showDeleteConfirmDialog = true },
                    onEditMetadata = { showEditChapterDialog = true },
                    onSaveAsTemplate = if (isEtudeMode) { {
                        viewModel.saveAsTemplate()
                        android.widget.Toast.makeText(context, "Modèle enregistré !", android.widget.Toast.LENGTH_SHORT).show()
                    } } else null
                )
            },
            bottomBar = { ScryBookBottomBar() }
        ) { innerPadding ->
            key(chapterId) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    EditorMainContent(
                        webView = webView,
                        onWebViewCreated = { webView = it },
                        htmlContent = htmlContent,
                        onContentChanged = { viewModel.updateContent(it) },
                        onInsertImage = { imagePickerLauncher.launch("image/*") },
                        fontSize = param.taille
                    )
                }
            }
        }
    }

    if (showNewChapterDialog) {
        var newChapNom by remember { mutableStateOf("") }
        var newChapNum by remember { mutableStateOf("") }
        var newChapResume by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewChapterDialog = false },
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
                        viewModel.saveNow()
                        viewModel.addChapitre(newChapNom, newChapNum, newChapResume)
                        showNewChapterDialog = false
                    }
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { showNewChapterDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showEditChapterDialog) {
        val ch = chapitre
        if (ch != null) {
            var editNom by remember(ch.id) { mutableStateOf(ch.nom) }
            var editNum by remember(ch.id) { mutableStateOf(ch.numero) }
            var editResume by remember(ch.id) { mutableStateOf(ch.resume) }
            AlertDialog(
                onDismissRequest = { showEditChapterDialog = false },
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
                    Button(onClick = {
                        viewModel.saveNow()
                        viewModel.updateChapitreInfo(ch.id, editNom, editNum, editResume)
                        showEditChapterDialog = false
                    }) { Text(stringResource(R.string.action_save)) }
                },
                dismissButton = { TextButton(onClick = { showEditChapterDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
            )
        }
    }

    if (showDeleteConfirmDialog) {
        val ch = chapitre
        if (ch != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.chapter_delete_title)) },
                text = { Text(stringResource(R.string.chapter_delete_confirm, ch.nom)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteChapitre(ch.id) {
                                showDeleteConfirmDialog = false
                                onBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopAppBar(
    chapitreNom: String,
    isSaving: Boolean,
    onBack: () -> Unit,
    onCharactersOpen: (() -> Unit)?,
    onPlacesOpen: (() -> Unit)?,
    onToggleTts: () -> Unit,
    isTtsPlaying: Boolean,
    ttsReady: Boolean,
    isSttListening: Boolean,
    onToggleStt: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onEditMetadata: () -> Unit,
    onSaveAsTemplate: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        navigationIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = "Sauvegarder", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        title = {
            Column {
                Text(
                    text = chapitreNom,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(isSaving) {
                    Text(
                        text = stringResource(R.string.editor_saving),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onEditMetadata) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.chapter_edit_title))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.chapter_delete_title), tint = MaterialTheme.colorScheme.error)
            }
            onCharactersOpen?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Default.Person, contentDescription = stringResource(R.string.nav_characters))
                }
            }
            onPlacesOpen?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Default.Place, contentDescription = stringResource(R.string.nav_places))
                }
            }
            IconButton(onClick = onToggleTts, enabled = ttsReady) {
                Icon(
                    if (isTtsPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = stringResource(R.string.action_tts),
                    tint = if (isTtsPlaying) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onToggleStt) {
                Icon(
                    if (isSttListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.action_stt),
                    tint = if (isSttListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            onSaveAsTemplate?.let { onSaveAs ->
                Box {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Plus d'options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Enregistrer comme modèle") },
                            onClick = {
                                showMenu = false
                                onSaveAs()
                            },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun EditorMainContent(
    webView: WebView?,
    onWebViewCreated: (WebView) -> Unit,
    htmlContent: String,
    onContentChanged: (String) -> Unit,
    onInsertImage: () -> Unit,
    fontSize: String
) {
    var isH1Active by remember { mutableStateOf(false) }
    var isH2Active by remember { mutableStateOf(false) }

    val bgColor = MaterialTheme.colorScheme.background
    val onBgColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    
    val bgColorHex = String.format("#%06X", (0xFFFFFF and bgColor.toArgb()))
    val onBgColorHex = String.format("#%06X", (0xFFFFFF and onBgColor.toArgb()))
    val primaryColorHex = String.format("#%06X", (0xFFFFFF and primaryColor.toArgb()))
    val outlineColorHex = String.format("#%06X", (0xFFFFFF and outlineColor.toArgb()))

    Column(modifier = Modifier.fillMaxSize().background(bgColor).imePadding()) {
        // Formatting toolbar
        FormattingToolbar(
            webView = webView,
            onInsertImage = onInsertImage,
            isH1Active = isH1Active,
            isH2Active = isH2Active
        )

        // WebView editor
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    
                    // Force dark mode if supported to fix contrast in system menus (spellcheck, etc.)
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                        val isDark = bgColor.red < 0.5f && bgColor.green < 0.5f && bgColor.blue < 0.5f 
                        WebSettingsCompat.setForceDark(
                            settings,
                            if (isDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                        )
                    }

                    setBackgroundColor(bgColor.toArgb())
                    
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onContentChanged(html: String) {
                            onContentChanged(html)
                        }

                        @JavascriptInterface
                        fun onFormatUpdate(h1: Boolean, h2: Boolean) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                isH1Active = h1
                                isH2Active = h2
                            }
                        }
                    }, "Android")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (htmlContent.isNotEmpty()) {
                                val escaped = htmlContent
                                    .replace("\\", "\\\\")
                                    .replace("'", "\\'")
                                    .replace("\n", "\\n")
                                view.evaluateJavascript("setContent('$escaped');", null)
                            }
                        }
                    }
                    loadDataWithBaseURL(null, getEditorHtml(bgColorHex, onBgColorHex, primaryColorHex, outlineColorHex, fontSize), "text/html", "UTF-8", null)
                    onWebViewCreated(this)
                }
            },
            update = { view ->
                if (htmlContent.isNotEmpty()) {
                    val escaped = htmlContent
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                    view.evaluateJavascript("if(document.getElementById('editor').innerHTML === '') setContent('$escaped');", null)
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

@Composable
private fun FormattingToolbar(
    webView: WebView?,
    onInsertImage: () -> Unit,
    isH1Active: Boolean,
    isH2Active: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item { ToolbarIconButton(Icons.Default.FormatBold, "document.execCommand('bold');", webView) }
            item { ToolbarIconButton(Icons.Default.FormatItalic, "document.execCommand('italic');", webView) }
            item { ToolbarIconButton(Icons.Default.FormatUnderlined, "document.execCommand('underline');", webView) }
            item { ToolbarIconButton(Icons.Default.StrikethroughS, "document.execCommand('strikeThrough');", webView) }
            
            item { VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp)) }
            
            item { ToolbarIconButton(Icons.Default.FormatClear, "document.execCommand('removeFormat'); document.execCommand('formatBlock', false, '<p>');", webView) }
            item { 
                val ts1 = if (isH1Active) "document.execCommand('formatBlock', false, '<p>');" else "document.execCommand('formatBlock', false, '<h1>');"
                ToolbarTextButton("T1", ts1, webView, isH1Active) 
            }
            item { 
                val ts2 = if (isH2Active) "document.execCommand('formatBlock', false, '<p>');" else "document.execCommand('formatBlock', false, '<h2>');"
                ToolbarTextButton("T2", ts2, webView, isH2Active) 
            }
            
            item { VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp)) }
            
            item { ToolbarIconButton(Icons.Default.FormatAlignLeft, "document.execCommand('justifyLeft');", webView) }
            item { ToolbarIconButton(Icons.Default.FormatAlignCenter, "document.execCommand('justifyCenter');", webView) }
            item { ToolbarIconButton(Icons.Default.FormatAlignRight, "document.execCommand('justifyRight');", webView) }
            item { ToolbarIconButton(Icons.Default.FormatAlignJustify, "document.execCommand('justifyFull');", webView) }
            
            item { VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp)) }
            
            item { ToolbarIconButton(Icons.Default.FormatListBulleted, "document.execCommand('insertUnorderedList');", webView) }
            item { ToolbarIconButton(Icons.Default.FormatListNumbered, "document.execCommand('insertOrderedList');", webView) }
            
            item { VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp)) }
            
            item { ToolbarIconButton(Icons.Default.FormatIndentIncrease, "document.execCommand('indent');", webView) }
            item { ToolbarIconButton(Icons.Default.FormatIndentDecrease, "document.execCommand('outdent');", webView) }

            item { VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp)) }

            item { ToolbarIconButton(Icons.Default.Image, "", webView, onClick = onInsertImage) }
            
            item { VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp)) }
            
            item { ToolbarIconButton(Icons.Default.Undo, "document.execCommand('undo');", webView) }
            item { ToolbarIconButton(Icons.Default.Redo, "document.execCommand('redo');", webView) }
        }
    }
}

@Composable
private fun ToolbarIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, jsCommand: String, webView: WebView?, onClick: (() -> Unit)? = null) {
    IconButton(
        onClick = { 
            if (onClick != null) onClick() 
            else webView?.evaluateJavascript(jsCommand, null) 
        },
        modifier = Modifier.size(36.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToolbarTextButton(label: String, jsCommand: String, webView: WebView?, isActive: Boolean = false) {
    val activeContainerColor = MaterialTheme.colorScheme.primaryContainer
    val activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    
    TextButton(
        onClick = { webView?.evaluateJavascript(jsCommand, null) },
        modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (isActive) activeContainerColor else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (isActive) activeContentColor else MaterialTheme.colorScheme.primary
        )
    ) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

private fun getEditorHtml(bgColor: String, textColor: String, accentColor: String, outlineColor: String, fontSize: String): String {
    val baseSize = fontSize.toIntOrNull() ?: 14
    val h1Size = baseSize + 6
    val h2Size = baseSize + 2
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<meta name="color-scheme" content="light dark">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: Georgia, serif;
    font-size: ${fontSize}px !important;
    line-height: 1.6;
    text-align: justify;
    background: $bgColor;
    color: $textColor;
    padding: 16px;
    min-height: 100vh;
    overflow-x: hidden;
  }
  #editor {
    outline: none;
    min-height: 100%;
    caret-color: $accentColor;
    word-wrap: break-word;
  }
  #editor * {
    font-size: inherit;
  }
  #editor p, #editor span, #editor font, #editor div {
    background-color: transparent !important;
    color: inherit !important;
  }
  h1 { display: block !important; text-align: center; font-size: ${h1Size}px !important; font-weight: normal; margin-top: 1em !important; margin-bottom: 0.5em !important; color: $accentColor; }
  h1 * { font-size: ${h1Size}px !important; }
  h2 { display: block !important; text-align: left; font-size: ${h2Size}px !important; font-weight: bold; text-decoration: underline; margin-top: 1em !important; margin-bottom: 0.5em !important; margin-left: 2em !important; }
  h2 * { font-size: ${h2Size}px !important; }
  p { margin-bottom: 0.8em; font-size: ${fontSize}px !important; }
  p * { font-size: ${fontSize}px !important; }
  ul, ol { margin-left: 20px; margin-bottom: 0.8em; }
  img { max-width: 100%; height: auto; display: block; margin: 10px auto; border-radius: 4px; }
</style>
</head>
<body>
<div id="editor" contenteditable="true" spellcheck="true"></div>
<script>
  var editor = document.getElementById('editor');
  var timer = null;

  function cleanHtml(html) {
    if (!html) return '';
    var div = document.createElement('div');
    div.innerHTML = html;
    var all = div.querySelectorAll('*');
    for (var i = 0; i < all.length; i++) {
        var el = all[i];
        if (el.nodeType === 1) { // Element
            el.removeAttribute('style');
            el.removeAttribute('class');
            if (el.tagName.toLowerCase() === 'font') {
                el.removeAttribute('color');
                el.removeAttribute('face');
                el.removeAttribute('size');
            }
        }
    }
    return div.innerHTML;
  }

  editor.addEventListener('input', function() {
    clearTimeout(timer);
    timer = setTimeout(function() {
      var cleaned = cleanHtml(editor.innerHTML);
      Android.onContentChanged(cleaned);
    }, 500);
  });

  editor.addEventListener('paste', function(e) {
    e.preventDefault();
    var html = (e.originalEvent || e).clipboardData.getData('text/html');
    var text = (e.originalEvent || e).clipboardData.getData('text/plain');
    if (html) {
        var cleaned = cleanHtml(html);
        document.execCommand('insertHTML', false, cleaned);
    } else {
        document.execCommand('insertText', false, text);
    }
  });

  function sendFormatUpdate() {
    var sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return;
    var node = sel.anchorNode;
    var isH1 = false;
    var isH2 = false;
    while (node && node.id !== 'editor') {
      if (node.nodeName && node.nodeName.toLowerCase() === 'h1') isH1 = true;
      if (node.nodeName && node.nodeName.toLowerCase() === 'h2') isH2 = true;
      node = node.parentNode;
    }
    if (window.Android && window.Android.onFormatUpdate) {
        window.Android.onFormatUpdate(isH1, isH2);
    }
  }

  document.addEventListener('selectionchange', sendFormatUpdate);
  editor.addEventListener('click', sendFormatUpdate);
  editor.addEventListener('keyup', sendFormatUpdate);

  function setContent(html) {
    editor.innerHTML = cleanHtml(html);
  }


  function insertTextAtCursor(text) {
    document.execCommand('insertText', false, text + ' ');
  }

  function insertImage(dataUri) {
    document.execCommand('insertHTML', false, '<img src="' + dataUri + '">');
  }

  function scrollToTitle(titleText) {
    var h1s = document.getElementsByTagName('h1');
    for (var i = 0; i < h1s.length; i++) {
      var text = h1s[i].innerText || h1s[i].textContent;
      if (text.trim() === titleText.trim()) {
        h1s[i].scrollIntoView({ behavior: 'smooth', block: 'start' });
        break;
      }
    }
  }

  // Focus helper
  document.addEventListener('click', function(e) {
    if (e.target.tagName !== 'A' && e.target.tagName !== 'IMG') {
        editor.focus();
    }
  });

  window.onload = function() {
    editor.focus();
  };
</script>
</body>
</html>
""".trimIndent()
}
