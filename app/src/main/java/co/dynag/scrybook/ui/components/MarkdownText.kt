package co.dynag.scrybook.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun ScryBookMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = TextUnit.Unspecified
) {
    if (content.isBlank()) return

    MarkdownText(
        markdown = content,
        modifier = modifier,
        style = style.copy(color = color, fontSize = if (fontSize != TextUnit.Unspecified) fontSize else style.fontSize),
        fontResource = null, // Use default font
        maxLines = Int.MAX_VALUE
    )
}
