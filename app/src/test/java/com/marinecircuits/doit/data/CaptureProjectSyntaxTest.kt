package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.CaptureSyntax.Completion
import com.marinecircuits.doit.data.CaptureSyntax.Kind
import com.marinecircuits.doit.data.CaptureSyntax.Parsed
import com.marinecircuits.doit.data.CaptureSyntax.Typing
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureProjectSyntaxTest {
    private val labels = listOf(LabelEntity(7, "Errand"), LabelEntity(8, "Waiting for reply"))
    private val projects = listOf(
        ProjectEntity(1, "Inbox", false, 0.0),
        ProjectEntity(2, "Work", false, 1.0, hexColor = "1973ff"),
        ProjectEntity(3, "Home office", false, 2.0),
        ProjectEntity(4, "Firmware", false, 3.0, parentProjectId = 2),
        ProjectEntity(5, "Old work", true, 4.0),
    )

    private fun parse(text: String) = CaptureSyntax.parse(text, labels, projects)
    private fun suggest(text: String, cursor: Int = text.length) =
        CaptureSyntax.suggestionsAt(text, cursor, labels, projects)

    @Test
    fun projectsAndLabelsLeaveTheTitleInEitherOrder() {
        assertEquals(Parsed("Order capacitors", listOf("Errand"), 2), parse("Order #work capacitors @errand"))
        assertEquals(Parsed("Order capacitors", listOf("Errand"), 2), parse("@Errand Order capacitors #WORK"))
    }

    @Test
    fun quotedNamesAndSubprojects() {
        assertEquals(Parsed("Buy lamp", emptyList(), 3), parse("""#"Home office" Buy lamp"""))
        assertEquals(Parsed("Flash board", emptyList(), 4), parse("Flash board #Firmware"))
    }

    @Test
    fun unknownArchivedAndIncompleteNamesStayLiteral() {
        for (text in listOf("Buy #unknown", "Buy #wo", "Buy #\"Old work\"", "Buy #\"Home office")) {
            assertEquals(Parsed(text, emptyList()), parse(text))
        }
        assertEquals(Parsed("Buy #unknown", emptyList(), 2), parse("Buy #unknown #Work"))
    }

    @Test
    fun hashesInsideWordsUrlsOrBeforeWhitespaceStayLiteral() {
        val text = "Read C# and https://example.com/#Work at # 5 +Work tomorrow !3 *x"
        assertEquals(Parsed(text, emptyList()), parse(text))
        assertNull(CaptureSyntax.typing("Read C#"))
        assertNull(CaptureSyntax.typing("https://example.com/#Wo"))
        assertNull(CaptureSyntax.typing("Buy # "))
    }

    @Test
    fun lastRecognizedProjectWins() {
        assertEquals(Parsed("Buy lamp", emptyList(), 3), parse("Buy #Work lamp #\"Home office\""))
        assertEquals(Parsed("Buy lamp #unknown", emptyList(), 3), parse("Buy #Work lamp #\"Home office\" #unknown"))
        assertEquals(Parsed("Buy lamp", emptyList(), 2), parse("Buy #Work lamp #work"))
    }

    @Test
    fun onlyKnownTokensLeaveNoTitle() {
        assertEquals(Parsed("", listOf("Errand"), 2), parse(" #Work @errand "))
        assertEquals(Parsed("", emptyList(), 2), parse("#Work"))
    }

    @Test
    fun prefixesInQuotedNamesAreNotParsedOrSuggestedSeparately() {
        val quotedProjects = projects + ProjectEntity(6, "Team @Errand", false, 5.0)
        val quotedLabels = labels + LabelEntity(9, "Review #Work")
        assertEquals(
            Parsed("Call", listOf("Review #Work"), 6),
            CaptureSyntax.parse("Call @\"Review #Work\" #\"Team @Errand\"", quotedLabels, quotedProjects),
        )
        val unknown = "Call #\"Missing @Errand\" @\"Missing #Work\""
        assertEquals(Parsed(unknown, emptyList()), parse(unknown))
        assertEquals(emptyList<CaptureSyntax.Suggestion>(), suggest("Call #\"Missing @Er"))
        assertEquals(emptyList<CaptureSyntax.Suggestion>(), suggest("Call @\"Missing #Wo"))
    }

    @Test
    fun typingUsesThePrefixAtTheCursor() {
        assertEquals(Typing(4, "wo", false, Kind.PROJECT), CaptureSyntax.typing("Buy #wo"))
        assertEquals(Typing(0, "", false, Kind.PROJECT), CaptureSyntax.typing("#"))
        assertEquals(Typing(0, "Home o", true, Kind.PROJECT), CaptureSyntax.typing("#\"Home o"))
        assertNull(CaptureSyntax.typing("#Work "))
        assertNull(CaptureSyntax.typing("#\"Home office\""))
        assertEquals(listOf("Work"), suggest("Buy #wo @er", 7).map { it.title })
        assertEquals(listOf("Errand"), suggest("Buy #wo @er").map { it.title })
    }

    @Test
    fun suggestionsExcludeArchivedProjectsAndRankPrefixBeforeSubstring() {
        assertEquals(listOf("Inbox", "Work", "Home office", "Firmware"), suggest("#").map { it.title })
        assertEquals(listOf("Work", "Firmware"), suggest("#w").map { it.title })
        assertEquals(listOf("Firmware", "Home office"), suggest("#FI").map { it.title })
        assertEquals("1973ff", suggest("#wo").single().hexColor)
        assertEquals(Kind.PROJECT, suggest("#wo").single().kind)
        assertEquals(emptyList<CaptureSyntax.Suggestion>(), suggest("#zz"))
        val many = (1L..8L).map { ProjectEntity(it, "Project $it", false, it.toDouble()) }
        assertEquals(5, CaptureSyntax.suggestionsAt("#", 1, labels, many).size)
    }

    @Test
    fun duplicateNamesSuggestAndResolveTheSameFirstActiveProject() {
        val duplicates = listOf(projects[1].copy(id = 9, archived = true)) + projects + projects[1].copy(id = 10, title = "work")
        assertEquals(2L, CaptureSyntax.parse("Buy #work", labels, duplicates).projectId)
        assertEquals(listOf("Work"), CaptureSyntax.suggestionsAt("#wo", 3, labels, duplicates).map { it.title })
    }

    @Test
    fun completionUsesHashQuotesSpacesAndPreservesFollowingText() {
        assertEquals("#Work ", CaptureSyntax.token("Work", Kind.PROJECT))
        assertEquals("#\"Home office\" ", CaptureSyntax.token("Home office", Kind.PROJECT))
        assertEquals(Completion("Buy #Work ", 10), CaptureSyntax.complete("Buy #wo", 7, "Work"))
        assertEquals(Completion("#Work buy @Errand", 6), CaptureSyntax.complete("#wo buy @Errand", 3, "Work"))
        assertEquals(Completion("#\"Home office\" ", 15), CaptureSyntax.complete("#\"Home o", 8, "Home office"))
        assertNull(CaptureSyntax.complete("no project", 10, "Work"))
    }
}
