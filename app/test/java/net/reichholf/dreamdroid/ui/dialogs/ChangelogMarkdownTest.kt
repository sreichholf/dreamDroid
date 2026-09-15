package net.reichholf.dreamdroid.ui.dialogs

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangelogMarkdownTest {
    @Test
    fun readChangelogUtf8ReadsText() {
        val input = ByteArrayInputStream("hello changelog".toByteArray(Charsets.UTF_8))
        assertEquals("hello changelog", readChangelogUtf8(input))
    }

    @Test
    fun readChangelogUtf8ReturnsNullOnIoFailure() {
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("boom")
        }
        assertNull(readChangelogUtf8(failing))
    }

    @Test
    fun parseChangelogMarkdownHeadings() {
        val blocks = parseChangelogMarkdown(
            """
            ### IMPORTANT: enable certificates
            ## 2.0.461
            """.trimIndent()
        )
        assertEquals(
            listOf(
                ChangelogBlock.Heading3("IMPORTANT: enable certificates"),
                ChangelogBlock.Heading2("2.0.461")
            ),
            blocks
        )
    }

    @Test
    fun parseChangelogMarkdownListItems() {
        val blocks = parseChangelogMarkdown(
            """
            * NEW: MultiEPG
            * FIX: screenshots
            """.trimIndent()
        )
        assertEquals(
            listOf(
                ChangelogBlock.ListItem("NEW: MultiEPG"),
                ChangelogBlock.ListItem("FIX: screenshots")
            ),
            blocks
        )
        assertEquals("\u2022 NEW: MultiEPG", blocks[0].displayText())
        assertEquals("\u2022 FIX: screenshots", blocks[1].displayText())
    }

    @Test
    fun parseChangelogMarkdownParagraphsAndBlankLines() {
        val blocks = parseChangelogMarkdown(
            """
            First paragraph.

            ## 1.0
            * item
            """.trimIndent()
        )
        assertEquals(
            listOf(
                ChangelogBlock.Paragraph("First paragraph."),
                ChangelogBlock.Heading2("1.0"),
                ChangelogBlock.ListItem("item")
            ),
            blocks
        )
    }

    @Test
    fun parseChangelogMarkdownGermanHeadingsAndLists() {
        val blocks = parseChangelogMarkdown(
            """
            ### WICHTIG: Zertifikate
            ## 2.0.461
            * NEU: MultiEPG
            """.trimIndent()
        )
        assertEquals(
            listOf(
                ChangelogBlock.Heading3("WICHTIG: Zertifikate"),
                ChangelogBlock.Heading2("2.0.461"),
                ChangelogBlock.ListItem("NEU: MultiEPG")
            ),
            blocks
        )
    }
}
