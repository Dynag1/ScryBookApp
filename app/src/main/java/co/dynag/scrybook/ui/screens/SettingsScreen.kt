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
import androidx.hilt.navigation.compose.hiltViewModel
import co.dynag.scrybook.R
import co.dynag.scrybook.ui.components.ScryBookBottomBar
import co.dynag.scrybook.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    projectPath: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val param by viewModel.param.collectAsState()
    val saved by viewModel.saved.collectAsState()

    LaunchedEffect(projectPath) { viewModel.load(projectPath) }

    if (saved) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            viewModel.resetSaved()
            onBack()
        }
    }

    val themes = listOf("system", "dark", "light")
    val langues = listOf("system", "fr", "en")

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (saved) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // Taille de police
            Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = {
                    val cur = (param.taille.toIntOrNull() ?: 16)
                    if (cur > 8) viewModel.update(param.copy(taille = (cur - 1).toString()))
                }) { Icon(Icons.Default.Remove, null) }
                Text("${param.taille} pt", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    val cur = (param.taille.toIntOrNull() ?: 16)
                    if (cur < 48) viewModel.update(param.copy(taille = (cur + 1).toString()))
                }) { Icon(Icons.Default.Add, null) }
            }

            Divider()

            // Timer de sauvegarde
            Text(stringResource(R.string.settings_autosave), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = {
                    val cur = (param.saveTime.toIntOrNull() ?: 30)
                    if (cur > 10) viewModel.update(param.copy(saveTime = (cur - 10).toString()))
                }) { Icon(Icons.Default.Remove, null) }
                Text("${param.saveTime} s", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    val cur = (param.saveTime.toIntOrNull() ?: 30)
                    if (cur < 300) viewModel.update(param.copy(saveTime = (cur + 10).toString()))
                }) { Icon(Icons.Default.Add, null) }
            }

            Divider()

            // Langue
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                langues.forEach { lang ->
                    val label = when(lang) {
                        "system" -> stringResource(R.string.langue_system)
                        "fr" -> stringResource(R.string.langue_fr)
                        "en" -> stringResource(R.string.langue_en)
                        else -> lang.uppercase()
                    }
                    FilterChip(
                        selected = param.langue == lang,
                        onClick = { viewModel.update(param.copy(langue = lang)) },
                        label = { Text(label) }
                    )
                }
            }

            Divider()

            // Thème
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                themes.forEach { theme ->
                    val label = when(theme) {
                        "system" -> stringResource(R.string.theme_system)
                        "dark" -> stringResource(R.string.theme_dark)
                        "light" -> stringResource(R.string.theme_light)
                        else -> theme
                    }
                    FilterChip(
                        selected = param.theme == theme,
                        onClick = { viewModel.update(param.copy(theme = theme)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = { viewModel.save() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
