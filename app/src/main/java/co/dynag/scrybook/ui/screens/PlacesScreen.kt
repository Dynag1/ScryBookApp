package co.dynag.scrybook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import co.dynag.scrybook.R
import co.dynag.scrybook.data.model.Lieu
import co.dynag.scrybook.ui.components.ScryBookBottomBar
import co.dynag.scrybook.ui.components.ScryBookMarkdown
import co.dynag.scrybook.ui.viewmodel.PlacesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    projectPath: String,
    onBack: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val places by viewModel.places.collectAsState()
    val selected by viewModel.selected.collectAsState()

    LaunchedEffect(projectPath) { viewModel.load(projectPath) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.nav_places), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = { ScryBookBottomBar() },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.newPlace() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.AddLocationAlt, contentDescription = null)
            }
        }
    ) { padding ->
        if (places.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Map, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Text(stringResource(R.string.places_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(places, key = { it.id }) { lieu ->
                    PlaceCard(
                        lieu = lieu,
                        onClick = { viewModel.select(lieu) },
                        onDelete = { viewModel.delete(lieu.id) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    selected?.let { lieu ->
        PlaceEditSheet(
            lieu = lieu,
            onSave = { id, nom, desc -> viewModel.save(id, nom, desc) },
            onDismiss = { viewModel.select(null) }
        )
    }
}

@Composable
fun PlacesSidePanel(
    projectPath: String,
    onClose: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val places by viewModel.places.collectAsState()
    val selected by viewModel.selected.collectAsState()

    LaunchedEffect(projectPath) { viewModel.load(projectPath) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Mini top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
            }
            Text(
                text = stringResource(R.string.nav_places),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.newPlace() }) {
                Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (places.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Map, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Text(stringResource(R.string.places_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(places, key = { it.id }) { lieu ->
                    PlaceCard(
                        lieu = lieu,
                        onClick = { viewModel.select(lieu) },
                        onDelete = { viewModel.delete(lieu.id) }
                    )
                }
            }
        }
    }

    selected?.let { lieu ->
        PlaceEditSheet(
            lieu = lieu,
            onSave = { id, nom, desc -> viewModel.save(id, nom, desc) },
            onDismiss = { viewModel.select(null) }
        )
    }
}

@Composable
private fun PlaceCard(lieu: Lieu, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(24.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(lieu.nom.ifBlank { stringResource(R.string.place_unnamed) }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (lieu.desc.isNotBlank()) {
                    ScryBookMarkdown(
                        content = lieu.desc,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceEditSheet(lieu: Lieu, onSave: (Long, String, String) -> Unit, onDismiss: () -> Unit) {
    var nom by remember(lieu.id) { mutableStateOf(lieu.nom) }
    var desc by remember(lieu.id) { mutableStateOf(lieu.desc) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (lieu.id == 0L) stringResource(R.string.place_new) else stringResource(R.string.place_edit),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
            )
            OutlinedTextField(nom, { nom = it }, label = { Text(stringResource(R.string.place_name)) },
                leadingIcon = { Icon(Icons.Default.Place, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text(stringResource(R.string.place_description)) },
                minLines = 4, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSave(lieu.id, nom, desc) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
