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
    fun onlyAKnownProjectIsADestination() {
        assertEquals(CaptureSyntax.Destination.Project(2), CaptureSyntax.destination("#Work", labels, projects))
        assertEquals(CaptureSyntax.Destination.Project(2), CaptureSyntax.destination("  #work  ", labels, projects))
        assertEquals(CaptureSyntax.Destination.Project(3), CaptureSyntax.destination("#\"Home office\"", labels, projects))
        assertEquals(CaptureSyntax.Destination.Project(4), CaptureSyntax.destination("#Firmware", labels, projects))
        assertEquals(CaptureSyntax.Destination.Label(7), CaptureSyntax.destination("@errand", labels, projects))
        assertNull(CaptureSyntax.destination("#\"Old work\"", labels, projects))
        assertNull(CaptureSyntax.destination("#unknown", labels, projects))
        assertNull(CaptureSyntax.destination("#Home office", labels, projects))
        assertNull(CaptureSyntax.destination("Buy #Work", labels, projects))
        assertNull(CaptureSyntax.destination("#Work @errand", labels, projects))
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
        assertEquals(many.map { it.title }, CaptureSyntax.suggestionsAt("#", 1, labels, many).map { it.title })
    }

    @Test
    fun duplicateNamesUsePathsAndRoundTripToTheSelectedProject() {
        val duplicates = projects + listOf(
            ProjectEntity(6, "Home", false, 6.0),
            ProjectEntity(7, "Shopping", false, 7.0, parentProjectId = 2),
            ProjectEntity(8, "Shopping", false, 8.0, parentProjectId = 6),
            ProjectEntity(9, "Shopping", true, 9.0),
        )
        val suggestions = CaptureSyntax.suggestionsAt("#shop", 5, labels, duplicates)
        assertEquals(listOf("Work / Shopping", "Home / Shopping"), suggestions.map { it.title })
        suggestions.zip(listOf(7L, 8L)).forEach { (suggestion, id) ->
            val token = CaptureSyntax.token(suggestion.title, Kind.PROJECT)
            assertEquals(id, CaptureSyntax.parse("Buy $token", labels, duplicates).projectId)
            assertEquals(CaptureSyntax.Destination.Project(id), CaptureSyntax.destination(token, labels, duplicates))
        }
        assertNull(CaptureSyntax.parse("Buy #Shopping", labels, duplicates).projectId)
    }

    @Test
    fun identicalFullPathsAndRealPathNamesCannotSelectTheWrongProject() {
        val duplicates = listOf(
            ProjectEntity(1, "Work", false, 0.0),
            ProjectEntity(2, "Shopping", false, 1.0, parentProjectId = 1),
            ProjectEntity(3, "Shopping", false, 2.0, parentProjectId = 1),
            ProjectEntity(4, "Work / Shopping [2]", false, 3.0),
        )
        val suggestions = CaptureSyntax.suggestionsAt("#", 1, labels, duplicates)
        assertEquals(4, suggestions.map { it.title.lowercase() }.toSet().size)
        suggestions.zip(duplicates).forEach { (suggestion, project) ->
            assertEquals(project.id, CaptureSyntax.parse(CaptureSyntax.token(suggestion.title, Kind.PROJECT), labels, duplicates).projectId)
        }
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
