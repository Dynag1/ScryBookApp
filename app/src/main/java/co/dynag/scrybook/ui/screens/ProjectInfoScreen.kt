package co.dynag.scrybook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import co.dynag.scrybook.R
import co.dynag.scrybook.data.model.Info
import co.dynag.scrybook.ui.components.ScryBookBottomBar
import co.dynag.scrybook.ui.components.MarkdownTextField
import co.dynag.scrybook.ui.viewmodel.ProjectInfoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectInfoScreen(
    projectPath: String,
    onBack: () -> Unit,
    viewModel: ProjectInfoViewModel = hiltViewModel()
) {
    val info by viewModel.info.collectAsState()
    val saved by viewModel.saved.collectAsState()

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { selectedUri ->
            try {
                val original = context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
                if (original != null) {
                    val maxWidth = 1000
                    val ratio = original.height.toFloat() / original.width.toFloat()
                    val targetHeight = (maxWidth * ratio).toInt()
                    val resized = android.graphics.Bitmap.createScaledBitmap(original, maxWidth, targetHeight, true)
                    val out = java.io.ByteArrayOutputStream()
                    resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                    viewModel.update(info.copy(couverture = "data:image/jpeg;base64,$base64"))
                    resized.recycle()
                    original.recycle()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(projectPath) { viewModel.load(projectPath) }

    if (saved) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            viewModel.resetSaved()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.info_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = { ScryBookBottomBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (saved) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.info_saved), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            OutlinedTextField(
                value = info.titre,
                onValueChange = { viewModel.update(info.copy(titre = it)) },
                label = { Text(stringResource(R.string.info_book_title)) },
                leadingIcon = { Icon(Icons.Default.Title, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = info.stitre,
                onValueChange = { viewModel.update(info.copy(stitre = it)) },
                label = { Text(stringResource(R.string.info_subtitle)) },
                leadingIcon = { Icon(Icons.Default.Subtitles, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("Image de Couverture", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (info.couverture.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        val bitmap = remember(info.couverture) {
                            try {
                                val pureBase64 = info.couverture.substringAfter(",")
                                val decodedBytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)
                                android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            } catch (e: Exception) { null }
                        }
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Couverture",
                                modifier = Modifier.height(180.dp).fillMaxWidth(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            Text("Image invalide", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text("Aucune image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                            Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Choisir", style = MaterialTheme.typography.labelLarge)
                        }
                        if (info.couverture.isNotBlank()) {
                            TextButton(onClick = { viewModel.update(info.copy(couverture = "")) }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Text("Supprimer", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = info.auteur,
                onValueChange = { viewModel.update(info.copy(auteur = it)) },
                label = { Text(stringResource(R.string.info_author)) },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = info.date,
                onValueChange = { viewModel.update(info.copy(date = it)) },
                label = { Text(stringResource(R.string.info_date)) },
                leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            MarkdownTextField(
                value = info.resume,
                onValueChange = { viewModel.update(info.copy(resume = it)) },
                label = stringResource(R.string.info_summary),
                leadingIcon = { Icon(Icons.Default.Description, null) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
