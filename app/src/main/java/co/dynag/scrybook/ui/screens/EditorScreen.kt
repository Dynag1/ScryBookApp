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
import co.dynag.scrybook.ui.viewmodel.EditorViewModel
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

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
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    
    var showNewChapterDialog by remember { mutableStateOf(false) }
    var showEditChapterDialog by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    // Detection logic for permanent menu: Landscape OR Tablet (> 8 inches / sw600dp)
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val showPermanentUI = isLandscape || isTablet

    // Launcher for image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { selectedUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                val imgDir = java.io.File(projectPath, "img")
                if (!imgDir.exists()) imgDir.mkdirs()
                val fileName = "img_${System.currentTimeMillis()}.jpg"
                val destFile = java.io.File(imgDir, fileName)
                inputStream?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val path = destFile.absolutePath
                webView?.evaluateJavascript("insertImage('$path');", null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(projectPath, chapterId) {
        viewModel.loadChapitre(projectPath, chapterId)
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
                        onHeaderClick = { viewModel.saveNow(); onBack() }
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
                        onSave = { viewModel.saveNow() }
                    )
                }
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
                        SummaryPanel(
                            title = stringResource(R.string.chapter_summary),
                            resume = chapitre?.resume ?: "",
                            modifier = Modifier.width(250.dp),
                            onEditClick = { showEditChapterDialog = true }
                        )
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
                    onSave = { viewModel.saveNow() }
                )
            }
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
                    OutlinedTextField(newChapResume, { newChapResume = it }, label = { Text(stringResource(R.string.chapter_summary)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
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
                        OutlinedTextField(editResume, { editResume = it }, label = { Text(stringResource(R.string.chapter_summary)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
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
    onSave: () -> Unit
) {
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
        FormattingToolbar(webView = webView, onInsertImage = onInsertImage)

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
                    setBackgroundColor(bgColor.toArgb())
                    
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onContentChanged(html: String) {
                            onContentChanged(html)
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
private fun FormattingToolbar(webView: WebView?, onInsertImage: () -> Unit) {
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
            item { ToolbarTextButton("T1", "document.execCommand('formatBlock', false, '<h1>');", webView) }
            item { ToolbarTextButton("T2", "document.execCommand('formatBlock', false, '<h2>');", webView) }
            
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
private fun ToolbarTextButton(label: String, jsCommand: String, webView: WebView?) {
    TextButton(
        onClick = { webView?.evaluateJavascript(jsCommand, null) },
        modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

private fun getEditorHtml(bgColor: String, textColor: String, accentColor: String, outlineColor: String, fontSize: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
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
  h1 { display: block; text-align: center; font-size: 20px !important; font-weight: normal; margin: 2em 0 1.5em; color: $accentColor; }
  h1 * { font-size: 20px !important; }
  h2 { display: block; text-align: left; font-size: 14px !important; font-weight: bold; text-decoration: underline; margin: 1em 0 0.5em; }
  h2 * { font-size: 14px !important; }
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

  editor.addEventListener('input', function() {
    clearTimeout(timer);
    timer = setTimeout(function() {
      Android.onContentChanged(editor.innerHTML);
    }, 500);
  });

  function setContent(html) {
    editor.innerHTML = html;
  }

  function insertTextAtCursor(text) {
    document.execCommand('insertText', false, text + ' ');
  }

  function insertImage(path) {
    document.execCommand('insertHTML', false, '<img src="file://' + path + '">');
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
