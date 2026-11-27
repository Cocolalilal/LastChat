package me.rerere.rikkahub.ui.components.textselection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.ui.graphics.vector.ImageVector

internal data class QuickAskIconOption(
    val name: String,
    val icon: ImageVector,
)

internal val QUICK_ASK_ICON_OPTIONS: List<QuickAskIconOption> = listOf(
    QuickAskIconOption("Translate", Icons.Rounded.Translate),
    QuickAskIconOption("Lightbulb", Icons.Rounded.Lightbulb),
    QuickAskIconOption("Summarize", Icons.Rounded.Summarize),
    QuickAskIconOption("AutoAwesome", Icons.Rounded.AutoAwesome),
    QuickAskIconOption("Spellcheck", Icons.Rounded.Spellcheck),
    QuickAskIconOption("Code", Icons.Rounded.Code),
    QuickAskIconOption("Search", Icons.Rounded.Search),
    QuickAskIconOption("Psychology", Icons.Rounded.Psychology),
    QuickAskIconOption("EditNote", Icons.Rounded.EditNote),
    QuickAskIconOption("FactCheck", Icons.Rounded.FactCheck),
    QuickAskIconOption("FormatQuote", Icons.Rounded.FormatQuote),
    QuickAskIconOption("MenuBook", Icons.Rounded.MenuBook),
    QuickAskIconOption("QuestionAnswer", Icons.Rounded.QuestionAnswer),
    QuickAskIconOption("Bolt", Icons.Rounded.Bolt),
    QuickAskIconOption("BookmarkAdd", Icons.Rounded.BookmarkAdd),
)

internal fun quickAskIconForName(name: String): ImageVector {
    return QUICK_ASK_ICON_OPTIONS
        .firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?.icon
        ?: Icons.Rounded.AutoAwesome
}
