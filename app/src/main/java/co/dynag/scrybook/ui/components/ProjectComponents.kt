package co.dynag.scrybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange

@Composable
fun ProjectDrawerContent(
    chapitres: List<Chapitre>,
    onChapterOpen: (Long) -> Unit,
    onNewChapter: () -> Unit,
    selectedId: Long? = null,
    onHeaderClick: (() -> Unit)? = null,
    onTitleClick: ((String) -> Unit)? = null,
    selectedChapterContent: String? = null
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

                    val contentToUse = if (isSelected) selectedChapterContent ?: "" else ""
                    if (isSelected && isLandscape && contentToUse.isNotBlank()) {
                        val titles = remember(contentToUse) {
                            val regex = Regex("<h1[^>]*>(.*?)</h1>", RegexOption.IGNORE_CASE)
                            regex.findAll(contentToUse)
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
    var editedState by remember(resume) { mutableStateOf(TextFieldValue(resume)) }
    var isPreview by remember { mutableStateOf(false) }
    val isDirty = editedState.text != resume

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
                        onClick = { onSave(editedState.text) },
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
                        content = editedState.text.ifBlank { stringResource(R.string.full_summary_no_resume) },
                        modifier = Modifier.fillMaxSize(),
                        color = if (editedState.text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    MarkdownFormattingToolbar(
                        state = editedState,
                        onValueChange = { editedState = it },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = editedState,
                        onValueChange = { editedState = it },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
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
            var state by remember { mutableStateOf(TextFieldValue(value)) }
            LaunchedEffect(value) {
                if (value != state.text) {
                    state = state.copy(text = value)
                }
            }
            Column {
                MarkdownFormattingToolbar(
                    state = state,
                    onValueChange = {
                        state = it
                        onValueChange(it.text)
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = state,
                    onValueChange = {
                        state = it
                        onValueChange(it.text)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = minLines,
                    placeholder = { Text("Écrire en Markdown...") },
                    leadingIcon = leadingIcon
                )
            }
        }
    }
}

@Composable
fun MarkdownFormattingToolbar(
    state: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item { MarkdownToolbarButton(Icons.Default.FormatBold, "Gras") { onValueChange(applyMarkdownFormat(state, "**", "**")) } }
            item { MarkdownToolbarButton(Icons.Default.FormatItalic, "Italique") { onValueChange(applyMarkdownFormat(state, "*", "*")) } }
            item { MarkdownToolbarTextButton("T1") { onValueChange(applyLineMarkdown(state, "# ")) } }
            item { MarkdownToolbarTextButton("T2") { onValueChange(applyLineMarkdown(state, "## ")) } }
            item { MarkdownToolbarButton(Icons.Default.FormatListBulleted, "Liste") { onValueChange(applyLineMarkdown(state, "- ")) } }
            item { MarkdownToolbarButton(Icons.Default.FormatQuote, "Citation") { onValueChange(applyLineMarkdown(state, "> ")) } }
        }
    }
}

@Composable
private fun MarkdownToolbarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, description, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MarkdownToolbarTextButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(32.dp).widthIn(min = 32.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

fun applyMarkdownFormat(state: androidx.compose.ui.text.input.TextFieldValue, prefix: String, suffix: String): androidx.compose.ui.text.input.TextFieldValue {
    val text = state.text
    val selection = state.selection
    val selectedText = text.substring(selection.start, selection.end)
    val newText = text.replaceRange(selection.start, selection.end, "$prefix$selectedText$suffix")
    val newSelection = androidx.compose.ui.text.TextRange(selection.start + prefix.length, selection.end + prefix.length)
    return state.copy(text = newText, selection = newSelection)
}

fun applyLineMarkdown(state: androidx.compose.ui.text.input.TextFieldValue, prefix: String): androidx.compose.ui.text.input.TextFieldValue {
    val text = state.text
    val selection = state.selection
    if (selection.collapsed) {
        var lineStart = selection.start
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }
        val newText = text.replaceRange(lineStart, lineStart, prefix)
        val newSelection = androidx.compose.ui.text.TextRange(selection.start + prefix.length)
        return state.copy(text = newText, selection = newSelection)
    } else {
        val selectedText = text.substring(selection.start, selection.end)
        val newText = text.replaceRange(selection.start, selection.end, "$prefix$selectedText")
        val newSelection = androidx.compose.ui.text.TextRange(selection.start + prefix.length, selection.end + prefix.length)
        return state.copy(text = newText, selection = newSelection)
    }
}


