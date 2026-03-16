package co.dynag.scrybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.dynag.scrybook.R
import co.dynag.scrybook.data.model.Chapitre

@Composable
fun ProjectDrawerContent(
    chapitres: List<Chapitre>,
    onChapterOpen: (Long) -> Unit,
    onNewChapter: () -> Unit,
    selectedId: Long? = null,
    onHeaderClick: (() -> Unit)? = null,
    onTitleClick: ((String) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(modifier = Modifier.fillMaxHeight()) {
        // Drawer header
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth().then(
                if (onHeaderClick != null) Modifier.clickable { onHeaderClick() } else Modifier
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.drawer_chapters),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${chapitres.size} ${stringResource(R.string.drawer_chapter_count)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Chapter list in drawer
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(chapitres, key = { it.id }) { chapitre ->
                val isSelected = selectedId != null && selectedId == chapitre.id
                
                Column {
                    NavigationDrawerItem(
                        icon = {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    chapitre.numero.ifBlank { "—" },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        label = {
                            Text(
                                chapitre.nom, 
                                maxLines = 1, 
                                overflow = TextOverflow.Ellipsis, 
                                style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
                            )
                        },
                        selected = isSelected,
                        onClick = { onChapterOpen(chapitre.id) },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    if (isSelected && isLandscape && chapitre.contenuHtml.isNotBlank()) {
                        val titles = remember(chapitre.contenuHtml) {
                            val regex = Regex("<h1[^>]*>(.*?)</h1>", RegexOption.IGNORE_CASE)
                            regex.findAll(chapitre.contenuHtml)
                                .map { it.groupValues[1].replace(Regex("<[^>]*>"), "").trim() }
                                .filter { it.isNotBlank() }
                                .toList()
                        }
                        
                        Column(modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)) {
                            titles.forEach { title ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .clickable { onTitleClick?.invoke(title) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                                        )
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add chapter button at bottom of drawer
        HorizontalDivider()
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp)) },
            label = { Text(stringResource(R.string.action_new_chapter), style = MaterialTheme.typography.bodyMedium) },
            selected = false,
            onClick = onNewChapter,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun SummaryPanel(
    title: String,
    resume: String,
    modifier: Modifier = Modifier,
    onSave: (String) -> Unit
) {
    var editedResume by remember(resume) { mutableStateOf(resume) }
    var isPreview by remember { mutableStateOf(false) }
    val isDirty = editedResume != resume

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                
                // Mode Toggle
                IconButton(
                    onClick = { isPreview = !isPreview },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isPreview) Icons.Default.Edit else Icons.Default.Visibility,
                        contentDescription = if (isPreview) "Éditer" else "Aperçu",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(Modifier.width(8.dp))

                if (isDirty) {
                    Button(
                        onClick = { onSave(editedResume) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sauvegarder", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            if (isPreview) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ScryBookMarkdown(
                        content = editedResume.ifBlank { stringResource(R.string.full_summary_no_resume) },
                        modifier = Modifier.fillMaxSize(),
                        color = if (editedResume.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                OutlinedTextField(
                    value = editedResume,
                    onValueChange = { editedResume = it },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = {
                        Text(
                            stringResource(R.string.full_summary_no_resume),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
fun ScryBookBottomBar() {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val height = if (isLandscape) 80.dp else 40.dp
    
    Surface(
        modifier = Modifier.fillMaxWidth().height(height),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 1.dp
    ) {
        // Bar is empty but provides visual structure
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun MarkdownTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 3,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    var isPreview by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (leadingIcon != null) {
                Box(contentAlignment = Alignment.Center) {
                    leadingIcon()
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { isPreview = !isPreview },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    if (isPreview) Icons.Default.Edit else Icons.Default.Visibility,
                    contentDescription = if (isPreview) "Éditer" else "Aperçu",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (isPreview) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    ScryBookMarkdown(content = value.ifBlank { "—" })
                }
            }
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = minLines,
                placeholder = { Text("Écrire en Markdown...") },
                leadingIcon = leadingIcon
            )
        }
    }
}


