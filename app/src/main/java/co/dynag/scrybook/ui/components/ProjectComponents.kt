package co.dynag.scrybook.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onHeaderClick: (() -> Unit)? = null
) {
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
                        val isSelected = selectedId != null && selectedId == chapitre.id
                        Text(
                            chapitre.nom, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis, 
                            style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
                        )
                    },
                    selected = selectedId != null && selectedId == chapitre.id,
                    onClick = { onChapterOpen(chapitre.id) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
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
    onEditClick: (() -> Unit)? = null
) {
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
                if (onEditClick != null) {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            
            if (resume.isBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.full_summary_no_resume),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            text = resume,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Justify,
                            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                        )
                    }
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
