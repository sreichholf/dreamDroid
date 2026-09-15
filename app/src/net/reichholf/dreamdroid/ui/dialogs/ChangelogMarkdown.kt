package net.reichholf.dreamdroid.ui.dialogs

internal sealed class ChangelogBlock {
    data class Heading2(val text: String) : ChangelogBlock()
    data class Heading3(val text: String) : ChangelogBlock()
    data class ListItem(val text: String) : ChangelogBlock()
    data class Paragraph(val text: String) : ChangelogBlock()
}

internal fun parseChangelogMarkdown(markdown: String): List<ChangelogBlock> {
    val blocks = mutableListOf<ChangelogBlock>()
    for (rawLine in markdown.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty()) {
            continue
        }
        val block = when {
            line.startsWith("### ") -> ChangelogBlock.Heading3(line.removePrefix("### ").trim())
            line.startsWith("## ") -> ChangelogBlock.Heading2(line.removePrefix("## ").trim())
            line.startsWith("* ") -> ChangelogBlock.ListItem(line.removePrefix("* ").trim())
            else -> ChangelogBlock.Paragraph(line)
        }
        blocks += block
    }
    return blocks
}

internal fun ChangelogBlock.displayText(): String = when (this) {
    is ChangelogBlock.Heading2 -> text
    is ChangelogBlock.Heading3 -> text
    is ChangelogBlock.ListItem -> "\u2022 $text"
    is ChangelogBlock.Paragraph -> text
}
