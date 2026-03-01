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
import co.dynag.scrybook.data.model.Info
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
        }
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
            OutlinedTextField(
                value = info.resume,
                onValueChange = { viewModel.update(info.copy(resume = it)) },
                label = { Text(stringResource(R.string.info_summary)) },
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
