package co.dynag.scrybook.ui.screens

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
import co.dynag.scrybook.data.model.Personnage
import co.dynag.scrybook.ui.components.ScryBookBottomBar
import co.dynag.scrybook.ui.viewmodel.CharactersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(
    projectPath: String,
    onBack: () -> Unit,
    viewModel: CharactersViewModel = hiltViewModel()
) {
    val characters by viewModel.characters.collectAsState()
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
                title = {
                    Text(stringResource(R.string.nav_characters), fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = { ScryBookBottomBar() },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.newCharacter() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
            }
        }
    ) { padding ->
        if (characters.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Group, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Text(stringResource(R.string.characters_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(characters, key = { it.id }) { perso ->
                    CharacterCard(
                        perso = perso,
                        onClick = { viewModel.select(perso) },
                        onDelete = { viewModel.delete(perso.id) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Edit sheet
    selected?.let { perso ->
        CharacterEditSheet(
            perso = perso,
            onSave = { viewModel.save(it) },
            onDismiss = { viewModel.select(null) }
        )
    }
}

@Composable
private fun CharacterCard(perso: Personnage, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (perso.alias.ifBlank { perso.prenom }.ifBlank { perso.nom }).take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = perso.alias.ifBlank { "${perso.prenom} ${perso.nom}".trim() }.ifBlank { stringResource(R.string.character_unnamed) },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (perso.descGlobal.isNotBlank()) {
                    Text(perso.descGlobal, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun CharacterEditSheet(perso: Personnage, onSave: (Personnage) -> Unit, onDismiss: () -> Unit) {
    var alias by remember(perso.id) { mutableStateOf(perso.alias) }
    var nom by remember(perso.id) { mutableStateOf(perso.nom) }
    var prenom by remember(perso.id) { mutableStateOf(perso.prenom) }
    var sexe by remember(perso.id) { mutableStateOf(perso.sexe) }
    var age by remember(perso.id) { mutableStateOf(perso.age.toString().let { if (it == "0") "" else it }) }
    var descPhys by remember(perso.id) { mutableStateOf(perso.descPhys) }
    var descGlobal by remember(perso.id) { mutableStateOf(perso.descGlobal) }
    var skill by remember(perso.id) { mutableStateOf(perso.skill) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (perso.id == 0L) stringResource(R.string.character_new) else stringResource(R.string.character_edit),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
            )
            OutlinedTextField(alias, { alias = it }, label = { Text(stringResource(R.string.character_alias)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(prenom, { prenom = it }, label = { Text(stringResource(R.string.character_firstname)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(nom, { nom = it }, label = { Text(stringResource(R.string.character_lastname)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sexe, { sexe = it }, label = { Text(stringResource(R.string.character_gender)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(age, { age = it }, label = { Text(stringResource(R.string.character_age)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(descPhys, { descPhys = it }, label = { Text(stringResource(R.string.character_physical)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(descGlobal, { descGlobal = it }, label = { Text(stringResource(R.string.character_description)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(skill, { skill = it }, label = { Text(stringResource(R.string.character_skills)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    onSave(perso.copy(alias = alias, nom = nom, prenom = prenom, sexe = sexe,
                        age = age.toIntOrNull() ?: 0, descPhys = descPhys, descGlobal = descGlobal, skill = skill))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.action_save)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
