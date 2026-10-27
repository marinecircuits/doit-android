package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.local.LabelEntity

/**
 * `@label` in captured text, the same rule as doit for Linux and the Vikunja bar plugin: an `@` that starts a word,
 * followed by a name or a "quoted name". An `@` inside a word (an e-mail address) or followed by a space is plain text.
 */
object LabelSyntax {
    private val LABEL = Regex("""\s@("([^"]+)"|\S+)""")

    /** The label being typed at the end of the text before the cursor: `@`, `@na` or `@"two wo`. */
    private val TYPING = Regex("""(?:^|\s)@(?:"([^"]*)|(\S*))$""")
    const val SUGGESTIONS = 5

    data class Parsed(val title: String, val labels: List<String>)

    /** A label being typed: [start] is the index of its `@`, [query] what follows it (without the quote). */
    data class Typing(val start: Int, val query: String, val quoted: Boolean)

    /**
     * Splits captured text into the title and the labels it names. An `@name` naming one of the [known] labels,
     * ignoring case, leaves the title and becomes that label's title; any other `@name` stays in the title.
     */
    fun parse(text: String, known: List<LabelEntity>): Parsed {
        val byKey = known.associateBy({ it.title.lowercase() }, { it.title })
        var s = " $text "
        val names = mutableListOf<String>()
        var position = 0
        while (true) {
            val match = LABEL.find(s, position) ?: break
            val name = match.groups[2]?.value ?: match.groupValues[1]
            val title = byKey[name.trim().lowercase()]
            if (title == null) {
                position = match.range.first + 1 // not a label: plain text
                continue
            }
            if (title !in names) names += title
            s = s.substring(0, match.range.first) + " " + s.substring(match.range.last + 1)
            position = match.range.first
        }
        return Parsed(s.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" "), names)
    }

    fun typing(textBeforeCursor: String): Typing? {
        val match = TYPING.find(textBeforeCursor) ?: return null
        val start = match.range.first + if (match.value.startsWith("@")) 0 else 1
        val quoted = match.groups[1] != null
        return Typing(start, if (quoted) match.groupValues[1] else match.groupValues[2], quoted)
    }

    /** Up to [SUGGESTIONS] labels: names starting with [typed] first, then names containing it, ignoring case. */
    fun suggest(labels: List<LabelEntity>, typed: String): List<LabelEntity> {
        val query = typed.lowercase()
        val starts = labels.filter { it.title.lowercase().startsWith(query) }
        val contains = labels.filter { query.isNotEmpty() && query in it.title.lowercase() && it !in starts }
        return (starts + contains).take(SUGGESTIONS)
    }

    /** The labels to suggest while the text before [cursor] ends in a label being typed; empty otherwise. */
    fun suggestionsAt(text: String, cursor: Int, labels: List<LabelEntity>): List<LabelEntity> =
        typing(text.take(cursor))?.let { suggest(labels, it.query) }.orEmpty()

    data class Completion(val text: String, val cursor: Int)

    /** Replaces the label being typed before [cursor] with [name], or returns null when none is being typed. */
    fun complete(text: String, cursor: Int, name: String): Completion? {
        val typing = typing(text.take(cursor)) ?: return null
        val token = token(name)
        // The token ends in a space already.
        val rest = text.drop(cursor).removePrefix(" ")
        return Completion(text.take(typing.start) + token + rest, typing.start + token.length)
    }

    /** How a picked label is written into the field. */
    fun token(name: String): String = if (name.any { it.isWhitespace() }) "@\"$name\" " else "@$name "

    private val WHITESPACE = Regex("""\s+""")
}
