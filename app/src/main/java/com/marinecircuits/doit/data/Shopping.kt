package com.marinecircuits.doit.data

import androidx.core.text.HtmlCompat
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.ProjectEntity

/** Shared with Doit Linux: a visible, standalone line in the project's description. */
object Shopping {
    const val MARKER = "doit:type=shopping"
    private val quantity = Regex("""(.+?)\s+\[([^\[\]\r\n]+)\]\s*""")
    private val hidden = Regex("""<(script|style)\b[^>]*>.*?</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun isProject(description: String): Boolean {
        val text = if ('<' in description) HtmlCompat.fromHtml(description.replace(hidden, ""), HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                   else description
        return text.lineSequence().any { it.trim() == MARKER }
    }

    fun projectIds(projects: List<ProjectEntity>): Set<Long> =
        projects.filter { isProject(it.description) }.mapTo(HashSet()) { it.id }

    /** Presentation only: the original title stays in the database and on the server. */
    fun item(title: String): Pair<String, String?> {
        val match = quantity.matchEntire(title) ?: return title to null
        val name = match.groupValues[1].trim()
        val amount = match.groupValues[2].trim()
        return if (name.isNotEmpty() && amount.isNotEmpty()) name to amount else title to null
    }

    fun parseCapture(text: String, labels: List<LabelEntity>, projects: List<ProjectEntity>, fallback: Long?): CaptureSyntax.Parsed {
        val (name, amount) = item(text)
        val parsed = CaptureSyntax.parse(name, labels, projects)
        val destination = parsed.projectId ?: fallback
        if (amount != null && destination != null && destination in projectIds(projects)) {
            return parsed.copy(title = if (parsed.title.isEmpty()) "" else "${parsed.title} [$amount]")
        }
        return CaptureSyntax.parse(text, labels, projects)
    }
}
