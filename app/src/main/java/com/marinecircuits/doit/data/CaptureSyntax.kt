package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.ProjectEntity

/** Word-starting `@labels` and `#projects`, with quotes around names containing spaces. */
object CaptureSyntax {
    // Consume a quoted name as a whole, including an unfinished one, so prefixes inside it stay literal.
    private val TOKEN = Regex("""(?:^|\s)([@#])("[^"]*"?|[^\s"]*)""")
    private val WHITESPACE = Regex("""\s+""")
    enum class Kind(val prefix: Char) { LABEL('@'), PROJECT('#') }

    data class Parsed(val title: String, val labels: List<String>, val projectId: Long? = null)
    data class Typing(val start: Int, val query: String, val quoted: Boolean, val kind: Kind)
    data class Suggestion(val title: String, val hexColor: String, val kind: Kind)
    data class Completion(val text: String, val cursor: Int)

    /** Unknown names stay literal. The last recognized project wins; labels accumulate without duplicates. */
    fun parse(text: String, labels: List<LabelEntity>, projects: List<ProjectEntity> = emptyList()): Parsed {
        val labelsByName = labels.associateBy { it.title.lowercase() }
        val projectsByName = availableProjects(projects).associateBy { it.title.lowercase() }
        val names = mutableListOf<String>()
        var projectId: Long? = null
        var end = 0
        val title = buildString {
            for (match in TOKEN.findAll(text)) {
                val token = match.groupValues[2]
                if (token.startsWith('"') && (token.length < 2 || !token.endsWith('"'))) continue
                val key = token.removeSurrounding("\"").trim().lowercase()
                when (match.groupValues[1]) {
                    "@" -> {
                        val label = labelsByName[key] ?: continue
                        if (label.title !in names) names += label.title
                    }
                    "#" -> projectId = projectsByName[key]?.id ?: continue
                }
                append(text.substring(end, match.range.first))
                append(' ')
                end = match.range.last + 1
            }
            append(text.substring(end))
        }.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
        return Parsed(title, names, projectId)
    }

    /** The active token before the cursor; a closed quote or trailing space ends completion. */
    fun typing(textBeforeCursor: String): Typing? {
        val match = TOKEN.findAll(textBeforeCursor).lastOrNull() ?: return null
        if (match.range.last != textBeforeCursor.lastIndex) return null
        val token = match.groupValues[2]
        val quoted = token.startsWith('"')
        if (quoted && token.length > 1 && token.endsWith('"')) return null
        return Typing(
            start = match.groups[1]!!.range.first,
            query = if (quoted) token.drop(1) else token,
            quoted = quoted,
            kind = if (match.groupValues[1] == "@") Kind.LABEL else Kind.PROJECT,
        )
    }

    /** Matching labels or active projects, prefix matches before substring matches. */
    fun suggestionsAt(
        text: String,
        cursor: Int,
        labels: List<LabelEntity>,
        projects: List<ProjectEntity> = emptyList(),
    ): List<Suggestion> {
        val typing = typing(text.take(cursor)) ?: return emptyList()
        val candidates = when (typing.kind) {
            Kind.LABEL -> labels.map { Suggestion(it.title, it.hexColor, Kind.LABEL) }
            Kind.PROJECT -> availableProjects(projects).map { Suggestion(it.title, it.hexColor, Kind.PROJECT) }
        }
        val query = typing.query.lowercase()
        val starts = candidates.filter { it.title.lowercase().startsWith(query) }
        val contains = candidates.filter { query.isNotEmpty() && query in it.title.lowercase() && it !in starts }
        return starts + contains
    }

    /** Completes the token before the cursor, preserving the following text and keeping the cursor after it. */
    fun complete(text: String, cursor: Int, name: String): Completion? {
        val typing = typing(text.take(cursor)) ?: return null
        val token = token(name, typing.kind)
        val rest = text.drop(cursor).removePrefix(" ")
        return Completion(text.take(typing.start) + token + rest, typing.start + token.length)
    }

    fun token(name: String, kind: Kind): String =
        if (name.any { it.isWhitespace() }) "${kind.prefix}\"$name\" " else "${kind.prefix}$name "

    // A name always resolves to the first active project in the cached project order, including subprojects.
    // Suggest that same project only, so tapping a duplicate name cannot silently select another project.
    private fun availableProjects(projects: List<ProjectEntity>): List<ProjectEntity> =
        projects.filterNot { it.archived }.distinctBy { it.title.lowercase() }
}
